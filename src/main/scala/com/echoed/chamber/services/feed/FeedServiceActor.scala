package com.echoed.chamber.services.feed

import reflect.BeanProperty
import scala.collection.JavaConversions._
import com.echoed.chamber.dao.views._
import com.echoed.chamber.domain.views.{PartnerFeed, PublicFeed}
import org.springframework.beans.factory.FactoryBean
import akka.actor.{ActorSystem, Props, ActorRef, Actor}
import akka.util.Timeout
import akka.util.duration._
import akka.event.Logging
import com.echoed.chamber.dao.partner.PartnerDao
import com.echoed.chamber.domain.public.PartnerPublic


class FeedServiceActor extends FactoryBean[ActorRef] {
    
    @BeanProperty var feedDao: FeedDao = _
    @BeanProperty var partnerDao: PartnerDao = _

    @BeanProperty var timeoutInSeconds = 20
    @BeanProperty var actorSystem: ActorSystem = _

    def getObjectType = classOf[ActorRef]

    def isSingleton = true

    def getObject = actorSystem.actorOf(Props(new Actor {

    implicit val timeout = Timeout(timeoutInSeconds seconds)
    private final val logger = Logging(context.system, this)

    def receive = {
        case msg @ GetPublicFeed(page: Int) =>
            val channel = context.sender
            val limit = 30
            val start = msg.page * limit

            try {
                logger.debug("Attempting to retrieve Public Feed ")
                val echoes = asScalaBuffer(feedDao.getPublicFeed(start,limit)).toList
                val feed = new PublicFeed(echoes)
                channel ! GetPublicFeedResponse(msg, Right(feed))
            } catch {
                case e=>
                    channel ! GetPublicFeedResponse(msg, Left(new FeedException("Cannot get public feed", e)))
                    logger.error("Unexpected error processing {} , {}", msg, e)
            }

        case msg @ GetPublicCategoryFeed(categoryId: String, page: Int) =>
            val channel = context.sender
            val limit = 30
            val start = msg.page * limit

            try{
                logger.debug("Attempting to retrive Category Feed")
                val echoes = asScalaBuffer(feedDao.getCategoryFeed(categoryId, start, limit))
                val feed = new PublicFeed(echoes)
                channel ! GetPublicCategoryFeedResponse(msg, Right(feed))
            } catch {
                case e =>
                    channel ! GetPublicCategoryFeedResponse(msg, Left(new FeedException("Cannot get public category feed", e)))
                    logger.error("Unpexected Error processing {}, {}", msg, e)
            }


        case msg @ GetUserPublicFeed(echoedUserId: String, page:Int) =>
            val channel = context.sender
            val limit = 30
            val start = msg.page * limit
            try {
                logger.debug("Attempting to retrieve feed for user: ")
                val echoes = asScalaBuffer(feedDao.getEchoedUserFeed(echoedUserId, start, limit)).toList
                val stories = asScalaBuffer(feedDao.findStoryByEchoedUserId(echoedUserId)).toList
                val feed = new PublicFeed(echoes, stories)
                channel ! GetUserPublicFeedResponse(msg, Right(feed))
            } catch {
                case e =>
                    channel ! GetUserPublicFeedResponse(msg, Left(new FeedException("Cannot get user public feed", e)))
                    logger.error("Unexpected error processesing {}, {}", msg, e)
            }

        case msg @ GetPartnerFeed(partnerId: String, page: Int) =>
            val channel = context.sender

            try{
                val partnerId = msg.partnerId
                val limit = 30
                val start = msg.page * limit
                val echoes = asScalaBuffer(feedDao.getPartnerFeed(partnerId, start, limit)).toList
                val partner = partnerDao.findByIdOrHandle(partnerId)
                val partnerFeed = new PartnerFeed(new PartnerPublic(partner), echoes)
                channel ! GetPartnerFeedResponse(msg,Right(partnerFeed))
            } catch {
                case e =>
                    channel ! GetPartnerFeedResponse(msg, Left(new FeedException("Cannot get partner feed", e)))
                    logger.error("Unexpected error processesing {}, {}", msg, e)
            }

        case msg @ GetStory(storyId) =>
            val channel = context.sender

            try {
                channel ! GetStoryResponse(msg, Right(Option(feedDao.findStoryById(storyId))))
            } catch {
                case e =>
                    channel ! GetStoryResponse(msg, Left(new FeedException("Cannot get story %s" format storyId, e)))
                    logger.error("Unexpected error processing {}: {}", msg, e)
            }
    }

    }), "FeedService")
}
