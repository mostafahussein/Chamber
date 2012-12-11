package com.echoed.chamber.services.partner

import akka.actor.PoisonPill
import com.echoed.chamber.domain.{Topic, Notification}
import com.echoed.chamber.domain.partner.Partner
import com.echoed.chamber.domain.partner.PartnerSettings
import com.echoed.chamber.domain.partner.PartnerUser
import com.echoed.chamber.services._
import com.echoed.chamber.services.echoeduser.EchoedUserClientCredentials
import com.echoed.chamber.services.echoeduser.FollowPartner
import com.echoed.chamber.services.echoeduser.Follower
import com.echoed.chamber.services.echoeduser.RegisterNotification
import com.echoed.chamber.services.email.SendEmail
import com.echoed.chamber.services.state._
import com.echoed.util.DateUtils._
import com.echoed.util.{ScalaObjectMapper, Encrypter}
import feed.{RequestPartnerStoryFeedResponse, RequestPartnerStoryFeed}
import java.util.{Date, UUID}
import scala.Left
import scala.Right
import scala.Some
import com.echoed.chamber.domain.views.PartnerStoryFeed


class PartnerService(
        mp: MessageProcessor,
        ep: EventProcessor,
        encrypter: Encrypter,
        initMessage: Message,
        accountManagerEmail: String = "accountmanager@echoed.com",
        accountManagerEmailTemplate: String = "partner_accountManager_email") extends OnlineOfflineService {

    protected var partner: Partner = _
    private var partnerSettings: PartnerSettings = _
    private var partnerUser: Option[PartnerUser] = None
    private var topics = List[Topic]()


    private var followedByUsers = List[Follower]()


    override def preStart() {
        super.preStart()
        initMessage match {
            case msg: PartnerIdentifiable => mp.tell(ReadPartner(msg.credentials), self)
            case msg: RegisterPartner => //handled in init
        }
    }

    private def becomeOnlineAndRegister {
        becomeOnline
        context.parent ! RegisterPartnerService(partner)
    }

    def init = {
        case msg @ RegisterPartner(userName, email, siteName, siteUrl, shortName, community) =>
            mp.tell(QueryUnique(msg, msg, Option(sender)), self)

        case QueryUniqueResponse(QueryUnique(_, msg: RegisterPartner, Some(channel)), Left(e)) =>
            channel ! RegisterPartnerResponse(msg, Left(InvalidRegistration(e.asErrors())))
            self ! PoisonPill

        case QueryUniqueResponse(QueryUnique(_, msg: RegisterPartner, Some(channel)), Right(true)) =>
            partner = new Partner(msg.siteName, msg.siteUrl, msg.shortName, msg.community).copy(secret = encrypter.generateSecretKey)
            partnerSettings = new PartnerSettings(partner.id, partner.handle)

            val password = UUID.randomUUID().toString
            partnerUser = Some(new PartnerUser(msg.userName, msg.email)
                    .copy(partnerId = partner.id)
                    .createPassword(password))

            val code = encrypter.encrypt(
                    """{ "password": "%s", "createdOn": "%s" }"""
                    format(password, dateToLong(new Date)))


            channel ! RegisterPartnerResponse(msg, Right(partnerUser.get, partner))

            ep(PartnerCreated(partner, partnerSettings, partnerUser.get))
            becomeOnlineAndRegister

            val model = Map(
                "code" -> code,
                "partner" -> partner,
                "partnerUser" -> partnerUser)

            mp(SendEmail(
                partnerUser.get.email,
                "Your Echoed Account",
                "partner_email_register",
                model))

            mp(SendEmail(
                accountManagerEmail,
                "New partner %s" format partner.name,
                accountManagerEmailTemplate,
                model))

        case msg @ ReadPartnerResponse(_, Right(pss)) =>
            partner = pss.partner
            partnerSettings = pss.partnerSettings
            partnerUser = pss.partnerUser
            followedByUsers = pss.followedByUsers
            topics = pss.topics
            becomeOnlineAndRegister
    }

    def online = {

        case QueryFollowersForPartnerResponse(_, Right(f)) => followedByUsers = followedByUsers ++ f

        case msg: FetchPartner => sender ! FetchPartnerResponse(msg, Right(partner))

        case msg: FetchPartnerAndPartnerSettings =>
            sender ! FetchPartnerAndPartnerSettingsResponse(
                    msg,
                    Right(new PartnerAndPartnerSettings(partner, partnerSettings)))

        case msg @ ReadPartnerFeed(_, page, origin) =>
            val channel = sender
            mp(RequestPartnerStoryFeed(partner.id, page, origin)).onSuccess {
                case RequestPartnerStoryFeedResponse(_, Right(feed)) =>
                    channel ! ReadPartnerFeedResponse(msg, Right(new PartnerStoryFeed(partner, feed)))
            }

        case msg: ReadPartnerTopics =>
            val now = new Date
            sender ! ReadPartnerTopicsResponse(msg, Right(topics.filter(t => t.beginOn < now && t.endOn > now)))

        case msg @ RequestStory(_, topicId) =>
            sender ! RequestStoryResponse(msg, Right(RequestStoryResponseEnvelope(
                    partner,
                    partnerSettings,
                    topicId.flatMap(id => topics.find(_.id == id)))))


        case msg @ NotifyPartnerFollowers(_, eucc, n) =>
            val notification = n.copy(origin = partner, notificationType = Some("weekly"))
            var sendFollowRequest = true
            followedByUsers.foreach { f =>
                if (f.echoedUserId == eucc.id) sendFollowRequest = false
                else mp.tell(RegisterNotification(EchoedUserClientCredentials(f.echoedUserId), notification), self)
            }
            if (sendFollowRequest) mp(FollowPartner(eucc, partner.id))


        case msg @ AddPartnerFollower(_, eu) if (!followedByUsers.exists(_.echoedUserId == eu.id)) =>
            sender ! AddPartnerFollowerResponse(msg, Right(partner))
            followedByUsers = Follower(eu) :: followedByUsers

        case msg @ PutTopic(_, title, description, beginOn, endOn, topicId, community) =>
            try {
                val topic = topicId.flatMap(id => topics.find(_.id == id)).map { t =>
                        t.copy(
                                title = title,
                                description = description.orElse(t.description),
                                beginOn = beginOn.map(dateToLong(_)).getOrElse(t.beginOn),
                                endOn = endOn.map(dateToLong(_)).getOrElse(t.endOn),
                                updatedOn = new Date)
                    }.orElse(Some(new Topic(partner, title, description, beginOn, endOn)))
                    .map(t => t.copy(community = if (partner.isEchoed) community.getOrElse(t.community) else partner.category))
                    .map(t => if (t.beginOn > t.endOn) throw new InvalidDateRange() else t)
                    .map { topic =>
                        topics = if (topic.isUpdated) {
                            ep(TopicUpdated(topic))
                            topics.map(t => if (t.id == topic.id) topic else t)
                        } else {
                            ep(TopicCreated(topic))
                            topic :: topics
                        }
                        topic
                    }.get

                sender ! PutTopicResponse(msg, Right(topic))
            } catch {
                case e: InvalidDateRange => sender ! PutTopicResponse(msg, Left(e))
            }


        case msg @ PutPartnerCustomization(_, useGallery, useRemote, remoteVertical, remoteHorizontal, remoteOrientation, widgetTitle, widgetShareMessage) =>
            var customization = partnerSettings.makeCustomizationOptions
            customization += ("useGallery" -> useGallery)
            customization += ("useRemote" -> useRemote)
            customization += ("remoteVertical" -> remoteVertical)
            customization += ("remoteHorizontal" -> remoteHorizontal)
            customization += ("remoteOrientation" -> remoteOrientation)
            customization += ("widgetTitle" -> widgetTitle)
            customization += ("widgetShareMessage" -> widgetShareMessage)
            partnerSettings = partnerSettings.copy(updatedOn = new Date, customization = new ScalaObjectMapper().writeValueAsString(customization))
            ep(PartnerSettingsUpdated(partnerSettings))
            sender ! PutPartnerCustomizationResponse(msg, Right(partnerSettings.makeCustomizationOptions))

    }
}





