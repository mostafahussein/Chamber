package com.echoed.chamber.services.facebook

import reflect.BeanProperty
import com.echoed.util.ScalaObjectMapper
import java.util.Properties
import org.springframework.beans.factory.FactoryBean
import akka.actor._
import akka.util.Timeout
import akka.util.duration._
import akka.event.Logging
import dispatch._
import com.ning.http.client.RequestBuilder
import collection.mutable
import com.echoed.chamber.domain.FacebookFriend
import com.echoed.chamber.domain.FacebookLike
import com.echoed.chamber.domain.FacebookUser
import scala.Right
import com.echoed.chamber.domain.FacebookTestUser
import com.echoed.chamber.domain.FacebookComment
import scala.Left
import com.echoed.chamber.domain.views.FacebookPostData
import java.util.Date


class FacebookAccessDispatchActor extends FactoryBean[ActorRef] {


    @BeanProperty var clientId: String = _
    @BeanProperty var clientSecret: String = _
    @BeanProperty var redirectUrl: String = _
    @BeanProperty var canvasApp: String = _
    @BeanProperty var properties: Properties = _
    @BeanProperty var appNameSpace: String = _

    @BeanProperty var httpClient: Http = _

    private val facebook = "https://graph.facebook.com"

    @BeanProperty var timeoutInSeconds = 20
    @BeanProperty var actorSystem: ActorSystem = _

    def getObjectType = classOf[ActorRef]

    def isSingleton = true

    def getObject = actorSystem.actorOf(Props(new Actor {

    implicit val timeout = Timeout(timeoutInSeconds seconds)
    private final val logger = Logging(context.system, this)

    override def preStart {
        //NOTE: getting the properties like this is necessary due to a bug in Akka's Spring integration
        //where placeholder values were not being resolved
        {
            if (clientId == null) clientId = properties.getProperty("clientId")
            if (clientSecret == null) clientSecret = properties.getProperty("clientSecret")
            if (redirectUrl == null) redirectUrl = properties.getProperty("redirectUrl")
            if (canvasApp == null) canvasApp = properties.getProperty("canvasApp")
            if (appNameSpace == null) appNameSpace = properties.getProperty("appNameSpace")
            clientId != null && clientSecret != null && redirectUrl != null && canvasApp != null && appNameSpace != null
        } ensuring (_ == true, "Missing parameters")
    }


    private def path(path: String) = url(facebook) / path

    private def fetch(request: RequestBuilder)
            (callback: String => Unit)
            (errorCallback: FacebookException => Unit) {
        httpClient(request > { res =>
            val status = res.getStatusCode
            val body = res.getResponseBody
            logger.debug("{} {} from Facebook: {}", status, res.getStatusText, body)
            if (status >= 200 && status < 300) callback(body)
            else errorCallback(ScalaObjectMapper(body, classOf[error], true).asFacebookException)
        }).onFailure {
            case e => logger.error("Error executing {}: {}", request.subject.build().getUrl, e)
        }
    }

    private def graph[T](request: RequestBuilder, accessToken: String = null, valueType: Class[T] = classOf[String])
            (callback: T => Unit)
            (errorCallback: FacebookException => Unit) {
        val req = Option(accessToken).map { at =>
            if (request.subject.build().getMethod.toUpperCase == "POST") request << Map("access_token" -> accessToken)
            else request <<? Map("access_token" -> accessToken)
        }.getOrElse(request)

        fetch(req) { res =>
            if (valueType == classOf[String]) callback(res.asInstanceOf[T])
            else callback(ScalaObjectMapper(res, valueType))
        } (errorCallback)
    }


    def receive = {
        case msg @ FetchMe(accessToken) =>
            val channel = context.sender

            graph(path("me"), accessToken, classOf[Me]) { me =>
                channel ! FetchMeResponse(msg, Right(me.createFacebookUser(accessToken)))
            } { e => channel ! FetchMeResponse(msg, Left(e)) }


        case msg @ GetMe(code, queryString) =>
            val channel = context.sender

            val params = Map(
                    "code" -> code,
                    "redirect_uri" -> (redirectUrl + queryString),
                    "client_id" -> clientId,
                    "client_secret" -> clientSecret)

            graph(path("/oauth/access_token") <<? params) { res: String =>
                val accessToken = res.split("&").filter(_.startsWith("access_token"))(0).substring(13)
                graph(path("me"), accessToken, classOf[Me]) { me =>
                    channel ! GetMeResponse(msg, Right(me.createFacebookUser(accessToken)))
                } { e => channel ! GetMeResponse(msg, Left(e)) }
            } { e => channel ! GetMeResponse(msg, Left(e)) }


        case msg @ GetFriends(accessToken, facebookId, facebookUserId, _) =>
            val channel = context.sender

            def fetchFriends(request: RequestBuilder) {
                graph(request, accessToken, classOf[Friends]) { friends =>
                    logger.debug("Received {} friends for FacebookUser {}", friends.data.length, facebookUserId)
                    val facebookFriends = friends.data.map(_.createFacebookFriend(facebookUserId)).toList
                    if (facebookFriends.nonEmpty) channel ! GetFriendsResponse(msg, Right(facebookFriends))

                    Option(friends.paging).flatMap(p => Option(p.next)).foreach { url =>
                        logger.debug("Fetching next page of friends for FacebookUser {}: {}", facebookUserId, url)
                        fetchFriends(new RequestBuilder().setUrl(url))
                    }
                } { e => channel ! GetFriendsResponse(msg, Left(e)) }
            }

            fetchFriends(path("%s/friends" format facebookId))


        case msg @ Post(accessToken, facebookId, facebookPost) =>
            val channel = context.sender

            val params = Map(
                    "name" -> facebookPost.name,
                    "message" -> facebookPost.message,
                    "picture" -> facebookPost.picture,
                    "link" -> facebookPost.link,
                    "caption" -> facebookPost.caption,
                    "actions" -> ("""[{ "name": "View Echoed Exhibit", "link": "%s" }]""" format canvasApp))

            graph(path("/%s/feed" format facebookId) << params, accessToken, classOf[Id]) { res =>
                logger.debug("Successfully posted FacebookPost {}, facebookId {}", facebookPost.id, res)
                channel ! PostResponse(msg, Right(facebookPost.copy(facebookId = res.id)))
            } { e => channel ! PostResponse(msg, Left(e)) }


        case msg @ PublishAction(accessToken, action, obj, objUrl) =>
            val channel = context.sender

            graph(path("/me/%s:%s" format(appNameSpace, action)) << Map(obj -> objUrl), accessToken) { res =>
                logger.debug("Published action: {}", res)
                channel ! PublishActionResponse(msg, Right(true))
            } { e => channel ! PublishActionResponse(msg, Left(e)) }


        case msg @ GetPostData(facebookPostData) =>
            val channel = context.sender

            val facebookId = facebookPostData.facebookPost.facebookId
            val accessToken = facebookPostData.facebookUser.accessToken

            val postActor = context.actorOf(Props(new Actor() {

                var commentsDone = false
                var likesDone = false

                val comments = mutable.Buffer[FacebookComment]()
                val likes = mutable.Buffer[FacebookLike]()


                override def preStart() {
                    context.system.scheduler.scheduleOnce(1 minutes, context.self, 'timetodie)
                }

                def complete {
                    if (commentsDone && likesDone) {
                        facebookPostData.comments = comments.toList
                        facebookPostData.likes = likes.toList
                        channel ! GetPostDataResponse(msg, Right(facebookPostData))
                        context.self ! 'timetodie
                        logger.debug(
                            "Successfully fetched comments {} and likes {} for FacebookPost {}",
                            facebookPostData.comments.size,
                            facebookPostData.likes.size,
                            facebookPostData.id)
                    }
                }

                protected def receive = {
                    case ('comments, cmts: List[FacebookComment]) => comments ++= cmts
                    case 'commentsDone =>
                        commentsDone = true
                        complete
                    case ('likes, lks: List[FacebookLike]) => likes ++= lks
                    case 'likesDone =>
                        likesDone = true
                        complete
                    case 'timetodie =>
                        self ! PoisonPill
                        if (!commentsDone || !likesDone) {
                            logger.error(
                                    "Fetch of post data timed out with comments done = {} and likes done = {}",
                                    commentsDone,
                                    likesDone)
                        }
                }
            }))

            def fetchComments(request: RequestBuilder) {
                graph(request, accessToken, classOf[Comments]) { comments =>
                    logger.debug("Received {} comments for FacebookPost {}", comments.data.length, facebookPostData.id)
                    postActor ! ('comments, comments.data.map(_.createFacebookComment(facebookPostData)).toList)

                    Option(comments.paging).flatMap(p => Option(p.next)).map { url =>
                        logger.debug("Fetching next page of comments for FacebookPost {}: {}", facebookPostData.id, url)
                        fetchComments(new RequestBuilder().setUrl(url))
                        true
                    }.orElse {
                        postActor ! 'commentsDone
                        Option(false)
                    }
                } { e => logger.error("Error fetching comments for FacebookPost {}: {}", facebookPostData.id, e) }
            }

            def fetchLikes(request: RequestBuilder) {
                graph(request, accessToken, classOf[Likes]) { likes =>
                    logger.debug("Received {} likes for FacebookPost {}", likes.data.length, facebookPostData.id)
                    postActor ! ('likes, likes.data.map(_.createFacebookLike(facebookPostData)).toList)

                    Option(likes.paging).flatMap(p => Option(p.next)).map { url =>
                        logger.debug("Fetching next page of likes for FacebookPost {}: {}", facebookPostData.id, url)
                        fetchLikes(new RequestBuilder().setUrl(url))
                        true
                    }.orElse {
                        postActor ! 'likesDone
                        Option(false)
                    }
                } { e => logger.error("Error fetching likes for FacebookPost {}: {}", facebookPostData.id, e) }
            }

            fetchComments(path("%s/comments" format facebookId))
            fetchLikes(path("%s/likes" format facebookId))

        case msg @ Logout(accessToken) => context.sender ! LogoutResponse(msg, Right(true))
    }

    }), "FacebookAccessActor")
}

object FacebookAccessTest extends App {

//    val a = "access_token=AAAD8YEkH92ABADup5QuWNCDvlMo0qzn7t77UHddKhpr1TncZCL3lUOzmsC12qo6IDxfZCNbfQvVvinVLGOVClwO2zmJNcZCBDL87gjHgwZDZD&expires=5183953"
//
//    val token = a.split("&").filter(_.startsWith("access_token"))(0).substring(13) //flatMap(_.split("="))
//
//    throw new RuntimeException(token) //"end it already")

    val httpClient = new Http
    val endpoint = url("https://graph.facebook.com")

    val facebookClientId = "277490472318816"
    val facebookClientSecret = "93ea2feef64276d3cb457ba268828e6e"
    val facebookAppAccessToken= "277490472318816|jzXtF0cEQ2zwpvPvC74EVvzKfxA"

    val accessToken =
    "AAAChmwwiYUYBAAJVGhRLA9yy7SBvNJZCyWcrbu3okKXvCPQjAvMVVfxG57aj556ZCjWSIZCXzGpVFzlZBMGF5ZASoVoYeWqIsvlDnztCsogZDZD"
//    "AAACEdEose0cBAFn71pCXyhO8k3wYZCbcv3TVy66VB4H0iI44aAsEZBBDO7kwv5hhBpnozIVohtoVOShZBZCvF58LUdvkVAt9g72HIZBYIXgZDZD"
//    "AAAD8YEkH92ABALNo14yZAm2NMBtjIzTq90nVni5Mnauh4ov3mR66VhBYbxPh8CJ74GplO4DPT4FmGqnKFxSidVjUosctQ7uxr8mD5OQZDZD"

    val mapper = new ScalaObjectMapper

/*{"error":{"message":"Error validating access token: User 717551615 has not authorized application 277490472318816.","type":"OAuthException","code":190,"error_subcode":458}}
error object is error(Error validating access token: User 717551615 has not authorized application 277490472318816.,OAuthException,190)
*/

    def fetch(request: RequestBuilder)
            (callback: String => Unit)
            (errorCallback: FacebookException => Unit) {
        httpClient(request > { res =>
            val status = res.getStatusCode
            val body = res.getResponseBody
            if (status >= 200 && status < 300) callback(body)
            else {
                println("%s %s from Facebook: %s" format(status, res.getStatusText, body))
                errorCallback(mapper.readValue(body, classOf[error]).asFacebookException)
            }
        })
    }

    fetch(endpoint / "me" <<? Map("access_token" -> accessToken)) { res =>
        println("Received me %s" format res)
        val me = mapper.readValue(res, classOf[Me])
        println("Fetched me!: %s" format me)
    } { e => println("Received error! %s" format e)
    }

//    httpClient(endpoint / "me"
//            <<? Map("access_token" -> accessToken)
////            (addHeader("Accept", "application/json; charset=utf-8"))
//            > { res =>
//        println(res.getStatusCode)
//        println(res.getStatusText)
//        println(res.getResponseBody)
//        val error = mapper.readValue(res.getResponseBody, classOf[error])
//        println("error object is %s" format error)
//        httpClient.shutdown()
//    })
//            OK As.string).onSuccess { case res =>
//        println("Json is %s" format res)
//        val me = mapper.readValue(res, classOf[Me])
//    }.onFailure {
//        case e => println("Received error!: %s" format e)
//    }
}


case class error(message: String, `type`: String, code: Int, error_subcode: Int) {
    def asFacebookException =
        if (code == 190) OAuthFacebookException(message, null, `type`, code, error_subcode)
        else FacebookException(message, null, `type`, code, error_subcode)
}

case class Me(
        id: String,
        name: String,
        first_name: String,
        middle_name: String,
        last_name: String,
        link: String,
        gender: String,
        email: String,
        timezone: String,
        locale: String,
        verified: String,
        updated_time: String,
        birthday: String) {

    def createFacebookUser(accessToken: String) = new FacebookUser(
        null,
        id,
        name,
        email,
        link,
        gender,
        timezone,
        locale,
        accessToken
    )

    def createFacebookTestUser(loginUrl: String, accessToken: String, password: String = "1234567890") = new FacebookTestUser(
        id,
        name,
        email,
        password,
        loginUrl,
        accessToken)
}


case class From(id: String, name: String)

case class Comment(id: String, from: From, message: String, created_time: Date) {
    def createFacebookComment(facebookPostData: FacebookPostData) = new FacebookComment(
            facebookPostId = facebookPostData.id,
            facebookUserId = facebookPostData.facebookUser.id,
            echoedUserId = facebookPostData.facebookUser.echoedUserId,
            facebookId = id,
            byFacebookId = from.id,
            name = from.name,
            message = message,
            createdAt = created_time)
}

case class Comments(data: Array[Comment], paging: Paging)

case class Like(id: String, name: String) {
    def createFacebookLike(facebookPostData: FacebookPostData) = new FacebookLike(
            facebookPostId = facebookPostData.id,
            facebookUserId = facebookPostData.facebookUser.id,
            echoedUserId = facebookPostData.facebookUser.echoedUserId,
            facebookId = id,
            name = name)
}

case class Likes(data: Array[Like], paging: Paging)

case class Friend(id: String, name: String) {
    def createFacebookFriend(facebookUserId: String) = new FacebookFriend(facebookUserId, id, name)
}

case class Id(id: String)

case class Friends(data: Array[Friend], paging: Paging)

case class Paging(next: String, previous: String)






