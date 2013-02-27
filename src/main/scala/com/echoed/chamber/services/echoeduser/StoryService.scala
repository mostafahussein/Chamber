package com.echoed.chamber.services.echoeduser.story


import com.echoed.chamber.domain._
import com.echoed.chamber.services._
import com.echoed.chamber.services.adminuser.{AdminUserClientCredentials => AUCC}
import com.echoed.chamber.services.echoeduser._
import com.echoed.chamber.services.echoeduser.{EchoedUserClientCredentials => EUCC}
import com.echoed.chamber.services.partner.{NotifyPartnerFollowers, PartnerClientCredentials, RequestStory, RequestStoryResponse, RequestStoryResponseEnvelope}
import com.echoed.chamber.services.partneruser.{PartnerUserClientCredentials => PUCC}
import com.echoed.util.DateUtils._
import com.echoed.util.{ScalaObjectMapper, UUID}
import echoeduser.ChapterCreated
import echoeduser.ChapterUpdated
import echoeduser.CommentCreated
import echoeduser.CreateChapter
import echoeduser.CreateChapterResponse
import echoeduser.CreateStory
import echoeduser.CreateStoryResponse
import echoeduser.EchoedUserClientCredentials
import echoeduser.InitStory
import echoeduser.InitStoryResponse
import echoeduser.ModerateStory
import echoeduser.ModerateStoryResponse
import echoeduser.NewComment
import echoeduser.NewCommentResponse
import echoeduser.NewVote
import echoeduser.NewVoteResponse
import echoeduser.ProcessImage
import echoeduser.ProcessImageResponse
import echoeduser.RegisterNotification
import echoeduser.RegisterStory
import echoeduser.RequestImageUpload
import echoeduser.RequestImageUploadResponse
import echoeduser.StoryCreated
import echoeduser.StoryImageCreated
import echoeduser.StoryModerated
import echoeduser.StoryUpdated
import echoeduser.StoryViewed
import echoeduser.UpdateChapter
import echoeduser.UpdateChapterResponse
import echoeduser.UpdateCommunity
import echoeduser.UpdateCommunityResponse
import echoeduser.UpdateStory
import echoeduser.UpdateStoryResponse
import echoeduser.VoteCreated
import echoeduser.VoteUpdated
import java.util.{Properties, Date}
import org.apache.commons.codec.digest.DigestUtils
import scala.Left
import scala.Right
import scala.Some
import scala.collection.mutable.{Set => MSet}
import state._
import scala.concurrent.forkjoin.ThreadLocalRandom
import org.openqa.selenium.{JavascriptExecutor, OutputType, TakesScreenshot, WebDriver}
import org.openqa.selenium.firefox.{FirefoxBinary, FirefoxDriver}
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.mime.{HttpMultipartMode, MultipartEntity}
import org.apache.http.entity.mime.content.{FileBody, StringBody, ByteArrayBody}
import scala.io.Source
import scala.util.Try
import java.util.concurrent.TimeUnit
import java.io.File


class StoryService(
        mp: MessageProcessor,
        ep: EventProcessorActorSystem,
        initMessage: Message,
        echoedUser: EchoedUser,
        cloudinaryProperties: Properties,
        storyGraphUrl: String) extends OnlineOfflineService {

    import context.dispatcher

    private var storyState: StoryState = _
    private var temporaryLinks = Map.empty[String, Link]

    private def requestStory(partnerId: Option[String], topicId: Option[String] = None): Unit =
            mp.tell(RequestStory(PartnerClientCredentials(partnerId.getOrElse("Echoed")), topicId), self)

    private def readStory(storyId: String): Unit = mp.tell(ReadStory(storyId), self)

    override def preStart() {
        super.preStart()
        initMessage match {
            case msg @ InitStory(_, Some(storyId), _, _, _, _) => readStory(storyId)
            case msg @ InitStory(_, _, Some(echoId), _, _, _) => mp.tell(ReadStoryForEcho(echoId, echoedUser.id), self)
            case msg @ InitStory(_, _, _, partnerId, topicId, _) => requestStory(partnerId, topicId)
            case msg: StoryIdentifiable => readStory(msg.storyId)
        }
    }

    private def initStory(s: StoryState) {
        storyState = s
        context.parent ! RegisterStory(storyState.asStory)
        becomeOnline
    }

    def init = {
        case ReadStoryResponse(_, Left(StoryNotFound(_, _))) if (initMessage.isInstanceOf[CreateStory]) =>
             requestStory(initMessage.asInstanceOf[CreateStory].partnerId)
        case RequestStoryResponse(_, Right(RequestStoryResponseEnvelope(p, ps, t))) if (initMessage.isInstanceOf[CreateStory]) =>
            initStory(new StoryState(echoedUser, p, ps, topic = t).copy(id = initMessage.asInstanceOf[CreateStory].storyId))

        case ReadStoryResponse(_, Right(s)) => initStory(s)
        case ReadStoryForEchoResponse(_, Right(s)) => initStory(s)
        case ReadStoryForEchoResponse(_, Left(StoryForEchoNotFound(s, _))) => initStory(s)
        case RequestStoryResponse(_, Right(RequestStoryResponseEnvelope(p, ps, t))) =>
            initStory(new StoryState(echoedUser, p, ps, topic = t))
    }

    def online = {
        case msg: InitStory => sender ! InitStoryResponse(msg, Right(storyState.asStoryInfo))
        case msg: CreateStory if (storyState.isCreated) => sender ! CreateStoryResponse(msg, Right(storyState.asStory))

        case msg @ CreateStory(eucc, storyId, title, imageId, _, productInfo, community, _, topicId, contentType) =>
            val image = imageId.map(processImage(_))
            storyState = storyState.create(title, productInfo.orNull, community.orNull, image, contentType.orNull)
            ep(StoryCreated(storyState))
            sender ! CreateStoryResponse(msg, Right(storyState.asStory))
            notifyStoryUpdate(eucc)


        case msg @ UpdateStory(eucc, storyId, title, imageId, community, productInfo) =>
            val image = imageId.map(processImage(_))
            storyState = storyState.copy(
                    title = title,
                    imageId = image.map(_.id).orNull,
                    image = image,
                    community = community,
                    productInfo = productInfo.orNull,
                    updatedOn = new Date)
            ep(StoryUpdated(storyState))
            sender ! UpdateStoryResponse(msg, Right(storyState.asStory))
            notifyStoryUpdate(eucc)


        case msg @ NewVote(eucc, byEchoedUser, storyId, value) =>
            val vote = storyState.votes.get(byEchoedUser.id)
                    .map(_.copy(value = value, updatedOn = new Date))
                    .orElse(Option(new Vote("Story", storyId, byEchoedUser.id, value)))
                    .get

            storyState = storyState.copy(votes = storyState.votes + (vote.echoedUserId -> vote), updatedOn = new Date)
            if (vote.isUpdated) ep(VoteUpdated(storyState, vote)) else ep(VoteCreated(storyState, vote))
            if (value > 0 && !vote.isUpdated && (eucc.id != byEchoedUser.id)) {
                mp.tell(RegisterNotification(eucc, new Notification(
                    byEchoedUser,
                    "upvoted",
                    Map(
                        "subject" -> byEchoedUser.name,
                        "action" -> "upvoted",
                        "object" -> storyState.title,
                        "storyId" -> storyState.id))), self)
            }
            sender ! NewVoteResponse(msg, Right(storyState.votes))
            notifyStoryUpdate(eucc)


        case msg @ CreateChapter(eucc, storyId, title, text, imageIds, links, publish) =>
            val notifyPartnerFollowers = !storyState.isPublished && !storyState.partner.isEchoed
            val publishedOn: Long = if (publish.isEmpty || !publish.get) 0 else new Date

            val chapter = new Chapter(storyState.asStory, title, text).copy(publishedOn = publishedOn)

            val chapterImages = imageIds.map(processImage(_)).map { img =>
                new ChapterImage(chapter, img)
            }

            val chapterLinks = links.map(Link(chapter, _))

            storyState = storyState.copy(
                    chapters = storyState.chapters ::: List(chapter),
                    chapterImages = storyState.chapterImages ::: chapterImages,
                    links = storyState.links ::: chapterLinks,
                    updatedOn = new Date)

            ep(ChapterCreated(storyState, chapter, chapterImages, chapterLinks))
            sender ! CreateChapterResponse(msg, Right(ChapterInfo(chapter, chapterImages, chapterLinks)))
            notifyStoryUpdate(eucc)
            if (publishedOn > 0) {
                notifyFollowers(eucc, notifyPartnerFollowers)
                mp(PublishFacebookAction(eucc, "update", "story", storyGraphUrl + storyId))
            }


        case msg @ UpdateCommunity(eucc, storyId, communityId) =>
            storyState = storyState.copy(community = communityId)
            ep(StoryUpdated(storyState))
            sender ! UpdateCommunityResponse(msg, Right(storyState.asStory))
            notifyStoryUpdate(eucc)


        case msg @ UpdateChapter(eucc, storyId, chapterId, title, text, imageIds, links, publish) =>
            var chapter = storyState.chapters
                    .find(_.id == chapterId)
                    .map(_.copy(title = title, text = text, updatedOn = new Date))
                    .get


            chapter =
                if (!chapter.isPublished && publish.getOrElse(false)) {
                    //notify followers of new chapter and the partner's followers only if story has never been
                    //published before...
                    notifyFollowers(eucc, !storyState.isPublished && !storyState.partner.isEchoed)
                    chapter.copy(publishedOn = new Date)
                } else chapter

            val existingChapterImages = storyState.chapterImages.filter(_.chapterId == chapterId)
            val chapterImages = imageIds.map { id =>
                existingChapterImages.find(ci => id.contains(ci.imageId)).getOrElse {
                    new ChapterImage(chapter, processImage(id))
                }
            }

            val chapterLinks = links.map(Link(chapter, _))

            storyState = storyState.copy(
                    chapters = storyState.chapters.map(c => if (c.id == chapter.id) chapter else c),
                    chapterImages = storyState.chapterImages.filterNot(_.chapterId == chapter.id) ::: chapterImages,
                    links = storyState.links.filterNot(_.chapterId == chapter.id) ::: chapterLinks,
                    updatedOn = new Date)

            ep(ChapterUpdated(storyState, chapter, chapterImages, chapterLinks))
            sender ! UpdateChapterResponse(msg, Right(ChapterInfo(chapter, chapterImages, chapterLinks)))
            notifyStoryUpdate(eucc)


        case msg @ NewComment(eucc, byEchoedUser, storyId, chapterId, text, parentCommentId) =>
            val comment = new com.echoed.chamber.domain.Comment(
                storyState.chapters.find(_.id == chapterId).get,
                byEchoedUser,
                text,
                storyState.comments.find(_.id == parentCommentId))

            storyState = storyState.copy(
                    comments = storyState.comments ::: List(comment),
                    updatedOn = new Date)
            ep(CommentCreated(storyState, comment))

            sender ! NewCommentResponse(msg, Right(comment))
            notifyStoryFollowers(new Notification(
                    byEchoedUser,
                    "comment",
                    Map(
                        "subject" -> byEchoedUser.name,
                        "action" -> "commented on",
                        "object" -> storyState.title,
                        "storyId" -> storyState.id)))
            notifyStoryUpdate(eucc)


        case msg @ ModerateStory(_, _, eucc: EUCC, mo) if (eucc.id == echoedUser.id) =>
            moderate(msg, eucc.name.get, "EchoedUser", eucc.id, mo)
        case msg @ ModerateStory(_, _, pucc: PUCC, mo) if (pucc.partnerId.exists(_ == storyState.partner.id)) =>
            moderate(msg, pucc.name.get, "PartnerUser", pucc.id, mo)
        case msg @ ModerateStory(_, _, aucc: AUCC, mo) => moderate(msg, aucc.name.get, "AdminUser", aucc.id, mo)


        case msg: StoryViewed =>
            //we are not updating the updatedOn on purpose so the FeedService does not push the story to the top :(
            storyState = storyState.copy(views = storyState.views + 1) /*, updatedOn = new Date) */
            ep(StoryUpdated(storyState))
            notifyStoryUpdate(msg.credentials)


        case msg @ RequestImageUpload(eucc, storyId, callback) =>
            val params = Map(
                "timestamp" -> System.currentTimeMillis().toString,
                "callback" -> callback,
                "public_id" -> UUID(),
                "tags" -> "%s,%s".format(eucc.id, storyId),
                "transformation" -> "a_exif")

            val signature = sign(params)

            sender ! RequestImageUploadResponse(msg, Right(params ++ Map(
                "uploadUrl" -> cloudinaryProperties.getProperty("endpoint"),
                "api_key" -> cloudinaryProperties.getProperty("apiKey"),
                "cloudName" -> cloudinaryProperties.getProperty("name"),
                "signature" -> signature)))

//            val timestamp = System.currentTimeMillis
//            val name = cloudinaryProperties.getProperty("name")
//            val apiKey = cloudinaryProperties.getProperty("apiKey")
//            val secret = cloudinaryProperties.getProperty("secret")
//            val publicId = UUID()
//            val tags = "%s,%s" format(eucc.id, storyId)
//            val transformation = "a_exif"
//
//            val data = "callback=%s&public_id=%s&tags=%s&timestamp=%s&transformation=%s%s" format(
//                    callback,
//                    publicId,
//                    tags,
//                    timestamp,
//                    transformation,
//                    secret)
//            val signature = DigestUtils.shaHex(data)
//
//            sender ! RequestImageUploadResponse(msg, Right(Map(
//                    "uploadUrl" -> ("https://api.cloudinary.com/v1_1/%s/image/upload" format name),
//                    "timestamp" -> timestamp.toString,
//                    "callback" -> callback,
//                    "api_key" -> apiKey,
//                    "cloudName" -> name,
//                    "public_id" -> publicId,
//                    "tags" -> tags,
//                    "transformation" -> transformation,
//                    "signature" -> signature)))

        case msg @ ProcessImageResponse(_, Right(img)) =>
            if (storyState.imageId == img.id) {
                storyState = storyState.copy(image = Option(img), updatedOn = new Date)
                ep(StoryUpdated(storyState))
            } else storyState.chapterImages
                    .find(_.imageId == img.id)
                    .map(_.copy(image = img, updatedOn = new Date))
                    .foreach { chapterImage =>

                storyState.chapters
                        .find(_.id == chapterImage.chapterId)
                        .map(_.copy(updatedOn = new Date))
                        .foreach { chapter =>

                    val chapterImages = storyState.chapterImages.filter(_.chapterId == chapter.id).map { ci =>
                        if (ci.id == chapterImage.id) chapterImage else ci
                    }

                    storyState = storyState.copy(
                            chapters = storyState.chapters.map(c => if (c.id == chapter.id) chapter else c),
                            chapterImages = storyState.chapterImages.filterNot(_.chapterId == chapter.id) ::: chapterImages,
                            updatedOn = new Date)

                    ep(ChapterUpdated(storyState, chapter, chapterImages, storyState.links.filter(_.chapterId == chapter.id)))
                }
            }


        case msg @ PostLink(eucc, _, url) =>
            val u = if (!url.startsWith("http")) "http://" + url else url
            sender ! PostLinkResponse(msg, Right(temporaryLinks.get(u).getOrElse {
                val link = capture(Link(storyState.asStory, u))
                temporaryLinks += u -> link
                link
            }))
    }

    private def processImage(imageString: String) = {
        if (imageString.startsWith("{")) {
            val m = ScalaObjectMapper(imageString, classOf[Map[String, Any]])
            val image = Image(
                    m("id").toString,
                    m("url").toString,
                    m("width").asInstanceOf[Int],
                    m("height").asInstanceOf[Int],
                    m("cloudName").toString)
            ep(StoryImageCreated(image))
            self ! ProcessImageResponse(ProcessImage(Left(image)), Right(image))
            image
        } else {
            //throw new UnsupportedOperationException("Image processing no longer supported")
            mp(LookupImage(imageString)).onSuccess {
              case LookupImageResponse(msg, Right(image)) =>
                self ! ProcessImageResponse(ProcessImage(Left(image)), Right(image))
            }
            Image().copy(id = imageString)
        }
    }

    private def moderate(
            msg: ModerateStory,
            moderatedBy: String,
            moderatedRef: String,
            moderatedRefId: String,
            moderated: Boolean = true) {
        if (moderatedRefId != echoedUser.id || !storyState.isPublished) {
            storyState = storyState.moderate(moderatedBy, moderatedRef, moderatedRefId, moderated)
            ep(StoryModerated(storyState, storyState.moderations.head))
            notifyStoryUpdate(msg.credentials)
        }
        sender ! ModerateStoryResponse(msg, Right(storyState.moderationDescription))
    }


    private def notifyFollowers(eucc: EchoedUserClientCredentials, notifyPartnerFollowers: Boolean = false) {
        val notification = new Notification(
            echoedUser,
            "story updated",
            Map(
                "subject" -> echoedUser.name,
                "action" -> "updated story",
                "object" -> storyState.title,
                "storyId" -> storyState.id,
                "partnerId" -> storyState.partner.id,
                "partnerName" -> storyState.partner.name))
        mp.tell(NotifyFollowers(eucc, notification), self)
        if (notifyPartnerFollowers)
            mp.tell(NotifyPartnerFollowers(PartnerClientCredentials(storyState.partner.id), eucc, notification), self)

        notifyStoryFollowers(notification)
    }

    private def notifyStoryFollowers(notification: Notification) {
        //anybody that has commented on a Story implicitly follows the Story (including the owner)
        storyState.comments
                .foldLeft(MSet[String]() += echoedUser.id)((eus: MSet[String], c: Comment) => eus += c.byEchoedUserId)
                .filterNot(_ == notification.origin.id)
                .map(id => mp.tell(RegisterNotification(EUCC(id), notification), self))
    }

    private def notifyStoryUpdate(eucc: EchoedUserClientCredentials) {
        mp.tell(echoeduser.NotifyStoryUpdate(eucc, storyState.asStoryPublic), self)
    }


    private def sign(params: Map[String, String]) =
        DigestUtils.shaHex(
            params
                .toList
                .sorted
                .map { case (k, v) => "%s=%s" format(k, v) }
                .mkString("&") + cloudinaryProperties.getProperty("secret"))


    private def capture(link: Link): Link = {
        //so we should be using url2png.com for screen capture and general website checking service
        //WebDriver cannot and will probably never be able to get status codes so unless we want to fetch
        //the page twice we will have to live with bad pages (this is something url2png.com solves for us)
        //plus the below is a ton of blocking operations which need to be better managed this:
        //See "Blocking Needs Careful Management" section of http://doc.akka.io/docs/akka/snapshot/general/actor-systems.html

        var xvfb: Process = null
        var driver: WebDriver = null
        var pageTitle: String = null
        var screenShot: File = null
        var croppedScreenShot: File = null
//        var bytes: Array[Byte] = null

        val xfvbExec = "/usr/bin/Xvfb"
        val timeout = 20

        try {
            val display = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE - 1)
            log.debug("Starting up {} :{}", xfvbExec, display)
            xvfb = Runtime.getRuntime().exec("%s :%s" format(xfvbExec, display))

            log.debug("Starting Firefox on display {}", display)
            val firefox = new FirefoxBinary()
            firefox.setEnvironmentProperty("DISPLAY", ":" + display)
            driver = new FirefoxDriver(firefox, null)
            driver.manage().timeouts().pageLoadTimeout(timeout, TimeUnit.SECONDS)

            log.debug("Fetching {}", link.url)
            driver.get(link.url)
            pageTitle = driver.getTitle
            if (pageTitle == "Problem loading page") {
                log.warning("Problem loading {}", link.url)
                return link.copy(pageTitle = Some(pageTitle))
            }
//            driver.asInstanceOf[JavascriptExecutor].executeScript()

            log.debug("Taking screenshot of {}", link.url)
            screenShot = driver.asInstanceOf[TakesScreenshot].getScreenshotAs(OutputType.FILE)
//            bytes = driver.asInstanceOf[TakesScreenshot].getScreenshotAs(OutputType.BYTES)
            croppedScreenShot = File.createTempFile("cropped", ".png")
            croppedScreenShot.deleteOnExit()

            val cmd = "convert %s -crop 1920x1080+0+0 %s" format(
                screenShot.getAbsolutePath,
                croppedScreenShot.getAbsolutePath)
            log.debug("Cropping screenshot of {} using command {}", link.url, cmd)

            import scala.sys.process._
            val stderr = new StringBuilder()
            val exitCode = cmd.!(ProcessLogger(
                    line => stdout.append(line).append("\n"),
                    line => stderr.append(line).append("\n")))
            if (exitCode > 0) {
                log.warning("Could not crop screenshot of {}, exit code of command was {}, error output {}", link.url, exitCode, stderr)
                return link.copy(pageTitle = Some(pageTitle))
            }


            log.debug("Uploading to Cloudinary cropped screenshot of {}", link.url)
            val client = new DefaultHttpClient()
            val post = new HttpPost(cloudinaryProperties.getProperty("endpoint"))
            val multipart = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE)
            val params = Map(
                "timestamp" -> System.currentTimeMillis().toString,
                "public_id" -> UUID(),
                "tags" -> "%s,%s".format(link.echoedUserId, link.storyId))
            val signature = sign(params)
            (params ++ Map(
                "api_key" -> cloudinaryProperties.getProperty("apiKey"),
                "signature" -> signature)).foreach { case (key, value) => multipart.addPart(key, new StringBody(value)) }
//        multipart.addPart("file", new ByteArrayBody(bytes, "image/png", link.url))
            multipart.addPart("file", new FileBody(croppedScreenShot, "image/png", link.url))
            post.setEntity(multipart)
            val resp = client.execute(post)
            log.debug("Upload to Cloudinary of cropped screenshot of {} is {}", link.url, resp.getStatusLine.getStatusCode)
            val json = Source.fromInputStream(resp.getEntity.getContent, "US-ASCII").mkString
            log.debug("Response to upload to Cloudinary of screenshot of {} is {}", link.url, json)
            val map = ScalaObjectMapper(json, classOf[Map[String, Any]])
            val image = Image(
                    map("public_id").asInstanceOf[String],
                    map("url").asInstanceOf[String],
                    map("width").asInstanceOf[Int],
                    map("height").asInstanceOf[Int],
                    cloudinaryProperties.getProperty("name"))
            ep(StoryImageCreated(image))
            link.copy(pageTitle = Some(pageTitle), imageId = Some(image.id), image = image)
        } finally {
            Try(driver.quit())
            Try(xvfb.destroy())
            Try(screenShot.delete())
            Try(croppedScreenShot.delete())
        }
    }

}


object LinkTest extends App {
//    import dispatch._

    def sign(params: Map[String, String]) = {
        val secret = "1dgeEr1qX0WvW5SEiWHYFRsxXfc"

        DigestUtils.shaHex(
            params
                .toList
                .sorted
                .map { case (k, v) => "%s=%s" format(k, v) }
                .mkString("&") + secret)
//
//        val timestamp = System.currentTimeMillis
//        val name = "echoed-dev"
//        val apiKey = "875772213741827"
////        val name = cloudinaryProperties.getProperty("name")
////        val apiKey = cloudinaryProperties.getProperty("apiKey")
////        val secret = cloudinaryProperties.getProperty("secret")
//        val publicId = UUID()
//        val tags = "%s,%s" format(eucc.id, storyId)
//        val transformation = "a_exif"
//
//        val data = "callback=%s&public_id=%s&tags=%s&timestamp=%s&transformation=%s%s" format(
//            callback,
//            publicId,
//            tags,
//            timestamp,
//            transformation,
//            secret)
//        val signature = DigestUtils.shaHex(data)
//
//        sender ! RequestImageUploadResponse(msg, Right(Map(
//            "uploadUrl" -> ("https://api.cloudinary.com/v1_1/%s/image/upload" format name),
//            "timestamp" -> timestamp.toString,
//            "callback" -> callback,
//            "api_key" -> apiKey,
//            "cloudName" -> name,
//            "public_id" -> publicId,
//            "tags" -> tags,
//            "transformation" -> transformation,
//            "signature" -> signature)))
    }
//    val u = "http://google.com"
    val u = "http://psimadethis.com"
    var xvfb: Process = null
    var driver: WebDriver = null

    try {
        val display = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE - 1)
        xvfb = Runtime.getRuntime().exec("/usr/bin/Xvfb :%s" format display)

        val firefox = new FirefoxBinary()
        firefox.setEnvironmentProperty("DISPLAY", ":" + display)
        driver = new FirefoxDriver(firefox, null)

        driver.get(u)
        val title = driver.getTitle
        println("Retrieved %s" format title)
//        val bytes = driver.asInstanceOf[TakesScreenshot].getScreenshotAs(OutputType.BYTES)
        val file = driver.asInstanceOf[TakesScreenshot].getScreenshotAs(OutputType.FILE)
//        val js = "return arguments[0].getBoundingClientRect()";
//        val js = "return document.getElementsByTagName('body')[0].getBoundingClientRect()"
//        val obj = driver.asInstanceOf[JavascriptExecutor].executeScript(js).asInstanceOf[java.util.Map[String, String]]
//        const string javascript = "return arguments[0].getBoundingClientRect()";
//        var obj = (Dictionary<string, object>)((IJavaScriptExecutor)_core.Driver).ExecuteScript(javascript, element);
//    var rect = new Rectangle((int)double.Parse(obj["left"].ToString()),
//        (int)double.Parse(obj["top"].ToString()),
//        (int)double.Parse(obj["width"].ToString()),
//        (int)double.Parse(obj["height"].ToString()));
        val output = java.io.File.createTempFile("cropped", ".png")
        output.deleteOnExit()
        import scala.sys.process._
        val exitCode = ("convert %s -crop 1920x1080+0+0 %s" format(
//        val exitCode = ("convert %s -crop %sx768+0+%s %s" format(
                file.getAbsolutePath,
//                obj.get("width"),
//                obj.get("top"),
                output.getAbsolutePath)).!!
        println("Cropped screenshot - exit code %s" format exitCode)



//        val endpoint = "http://api.cloudinary.com/v1_1/echoed-dev/image/upload"
//        val timestamp = System.currentTimeMillis
//        val name = "echoed-dev"
//        val apiKey = "875772213741827"
//        val secret = "1dgeEr1qX0WvW5SEiWHYFRsxXfc"
//        val publicId = UUID()
//        val tags = "%s,%s" format(UUID(), UUID())
//
//        val data = "public_id=%s&tags=%s&timestamp=%s%s" format(
//            publicId,
//            tags,
//            timestamp,
//            secret)
//        val params = Map(
//            "timestamp" -> timestamp.toString,
//            "public_id" -> publicId,
//            "tags" -> tags)
//        val signature = sign(params)
////        val img = new File("/home/mpflueger/1.jpg")
//
//
////        println("Using Ning client")
////        val request = dispatch.url(endpoint)
////                        .setMethod("POST")
////                        .addBodyPart(new StringPart("cloud_name", name, "US-ASCII"))
////                        .addBodyPart(new StringPart("api_key", apiKey, "US-ASCII"))
////                        .addBodyPart(new StringPart("public_id", publicId, "US-ASCII"))
////                        .addBodyPart(new StringPart("tags", tags, "US-ASCII"))
////                        .addBodyPart(new StringPart("timestamp", timestamp.toString, "US-ASCII"))
////                        .addBodyPart(new StringPart("signature", signature, "US-ASCII"))
////                        .addBodyPart(new FilePart("file", img.getName, img, "application/octet-stream", "ISO-8859-1"))
//
////        val httpClient = dispatch.Http
////        httpClient(request > { res =>
////            val status = res.getStatusCode
////            val body = res.getResponseBody
////            println("%s (%s) response from Cloudinary:\n\n%s".format(status, res.getStatusText, body))
////        }).onFailure {
////            case e => println("We errored out! %s" format e)
////        }()
//
//
//
//        println("Using Apache client")
//        val client = new DefaultHttpClient()
//        val post = new HttpPost(endpoint)
//        val multipart = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE)
//        (params ++ Map(
//            "cloud_name" -> name,
//            "api_key" -> apiKey,
//            "signature" -> signature)).foreach { case (key, value) => multipart.addPart(key, new StringBody(value)) }
//        multipart.addPart("file", new ByteArrayBody(bytes, "image/png", u))
//        post.setEntity(multipart)
//        val resp = client.execute(post)
//        val stcode = resp.getStatusLine.getStatusCode
//        println("Status code is: %s" format stcode)
//        val json = Source.fromInputStream(resp.getEntity.getContent, "US-ASCII").mkString//.map(_.toByte).
//        println("JSON response is: %s" format json)
//        val map = ScalaObjectMapper(json, classOf[Map[String, String]])
//        println("url is: %s" format map("url"))
    } finally {
        driver.quit
        xvfb.destroy
    }
}
