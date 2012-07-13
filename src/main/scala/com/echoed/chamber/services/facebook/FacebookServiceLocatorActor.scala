package com.echoed.chamber.services.facebook

import reflect.BeanProperty
import scalaz._
import Scalaz._
import com.echoed.cache.{CacheEntryRemoved, CacheManager, CacheListenerActorClient}
import scala.collection.mutable.ConcurrentMap
import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConversions._
import java.util.Properties
import com.echoed.chamber.dao.partner.{PartnerSettingsDao, PartnerDao}
import com.echoed.chamber.dao.{FacebookUserDao, FacebookPostDao, FacebookFriendDao}
import org.springframework.beans.factory.FactoryBean
import akka.util.duration._
import akka.util.Timeout
import akka.event.Logging
import akka.pattern.ask
import com.echoed.chamber.domain.FacebookUser
import akka.actor._
import akka.actor.SupervisorStrategy.Restart
import com.echoed.chamber.services.EchoedActor


class FacebookServiceLocatorActor(
        cacheManager: CacheManager,
        facebookAccess: FacebookAccess,
        facebookUserDao: FacebookUserDao,
        facebookPostDao: FacebookPostDao,
        facebookFriendDao: FacebookFriendDao,
        partnerSettingsDao: PartnerSettingsDao,
        partnerDao: PartnerDao,
        echoClickUrl: String,
        implicit val timeout: Timeout = Timeout(20000)) extends EchoedActor {

    private var cache = cacheManager.getCache[FacebookService]("FacebookServices", Some(new CacheListenerActorClient(self)))
    private val cacheByFacebookId: ConcurrentMap[String, FacebookService] = new ConcurrentHashMap[String, FacebookService]()

    override val supervisorStrategy = OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
        case _: Exception ⇒ Restart
    }

    private def updateMe(me: FacebookUser) = {
        val facebookUser = Option(facebookUserDao.findByFacebookId(me.facebookId)) match {
            case Some(fu) =>
                log.debug("Found Facebook User {}", me.facebookId)
                fu.copy(accessToken = me.accessToken,
                        name = me.name,
                        email = me.email,
                        facebookId = me.facebookId,
                        link = me.link,
                        gender = me.gender,
                        timezone = me.timezone,
                        locale = me.locale)
            case None =>
                log.debug("No Facebook User {}", me.facebookId)
                me
        }

        log.debug("Updating FacebookUser {} accessToken {}", facebookUser.id, facebookUser.accessToken)
        facebookUserDao.insertOrUpdate(facebookUser)
        facebookUser
    }

    def handle = {
        case msg @ CacheEntryRemoved(facebookUserId: String, facebookService: FacebookService, cause: String) =>
            log.debug("Received {}", msg)
            facebookService.logout(facebookUserId)
            for ((key, fs) <- cacheByFacebookId if (fs.id == facebookService.id)) {
                cacheByFacebookId -= key
                log.debug("Removed {} from cache by Facebook id", fs.id)
            }
            log.debug("Sent logout for {}", facebookService)


        case msg @ LocateByCode(code, queryString) =>
            val me = context.self
            val channel = context.sender

            def error(e: Throwable) {
                channel ! LocateByCodeResponse(msg, Left(FacebookException("Could not locate Facebook user", e)))
                log.error("Error processing {}, {}", msg, e)
            }

            try {
                log.debug("Locating FacebookService with code {}", code)
                (me ? CreateFromCode(code, queryString)).onComplete(_.fold(
                    error(_),
                    _ match {
                        case CreateFromCodeResponse(_, Left(e)) => error(e)
                        case CreateFromCodeResponse(_, Right(facebookService)) =>
                            channel ! LocateByCodeResponse(msg, Right(facebookService))
                            facebookService.getFacebookUser.onComplete(_.fold(
                                log.error("Failed to cache FacebookService for code {}, {}", msg, _),
                                _ match {
                                    case GetFacebookUserResponse(_, Left(e)) =>
                                        log.error("Failed to cache FacebookService for code {}, {}", msg, e)
                                    case GetFacebookUserResponse(_, Right(facebookUser)) =>
                                        cache.put(facebookUser.id, facebookService)
                                        log.debug("Seeded cache with FacebookService key {}", code)
                                }))
                    }))
            } catch { case e => error(e) }


        case msg @ LocateByFacebookId(facebookId, accessToken) =>
            val me = context.self
            val channel = context.sender

            def error(e: Throwable) {
                channel ! LocateByFacebookIdResponse(msg, Left(FacebookException("Could not locate Facebook service", e)))
                log.error("Error processing {}, {}", msg, e)
            }

            def updateCache(facebookService: FacebookService) {
                facebookService.getFacebookUser.onComplete(_.fold(
                    log.error("Unable to update FacebookService cache by id", _),
                    _ match {
                        case GetFacebookUserResponse(_, Left(e)) =>
                            log.error("Unable to update FacebookService cache by id", e)
                        case GetFacebookUserResponse(_, Right(facebookUser)) =>
                            cache.put(facebookUser.id, facebookService)
                            log.debug("Updated FacebookService cache by id for {}", facebookUser.id)
                    }))
            }

            try {
                cacheByFacebookId.get(facebookId) match {
                    case Some(facebookService) =>
                        log.debug("Cache hit for FacebookService with facebookId {}", facebookId)
                        channel ! LocateByFacebookIdResponse(msg, Right(facebookService))
                        facebookService.updateAccessToken(accessToken)
                        updateCache(facebookService)
                    case None =>
                        log.debug("Cache miss for FacebookService with facebookId {}", facebookId)
                        (me ? CreateFromFacebookId(facebookId, accessToken)).onComplete(_.fold(
                            error(_),
                            _ match {
                                case CreateFromFacebookIdResponse(_, Left(e)) => error(e)
                                case CreateFromFacebookIdResponse(_, Right(facebookService)) =>
                                    channel ! LocateByFacebookIdResponse(msg, Right(facebookService))
                                    cacheByFacebookId.put(facebookId, facebookService)
                                    updateCache(facebookService)
                            }))
                }
            } catch { case e => error(e) }


        case msg @ LocateById(facebookUserId) =>
            val me = context.self
            val channel = context.sender

            def error(e: Throwable) {
                channel ! LocateByIdResponse(msg, Left(FacebookException("Could not locate Facebook user", e)))
                log.error("Error processing {}, {}", msg, e)
            }

            try {
                cache.get(facebookUserId) match {
                    case Some(facebookService) =>
                        log.debug("Cache hit for FacebookService with facebookUserId {}", facebookUserId)
                        channel ! LocateByIdResponse(msg, Right(facebookService))
                    case None =>
                        log.debug("Cache miss for FacebookService with facebookUserId {}", facebookUserId)
                        (me ? CreateFromId(facebookUserId)).onComplete(_.fold(
                            error(_),
                            _ match {
                                case CreateFromIdResponse(_, Left(e: FacebookUserNotFound)) =>
                                    channel ! LocateByIdResponse(msg, Left(e))
                                    log.debug("Facebook user {} not found", facebookUserId)
                                case CreateFromIdResponse(_, Left(e)) => error(e)
                                case CreateFromIdResponse(_, Right(facebookService)) =>
                                    channel ! LocateByIdResponse(msg, Right(facebookService))
                                    cache.put(facebookUserId, facebookService)
                                    log.debug("Cached {}", facebookService)
                            }))
                }
            } catch { case e => error(e) }


        case msg @ Logout(facebookUserId) =>
            val channel = context.sender

            try {
                log.debug("Processing {}", msg)
                cache.remove(facebookUserId).cata(
                    fs => {
                        channel ! LogoutResponse(msg, Right(true))
                        log.debug("Logged out FacebookUser {} ", facebookUserId)
                    },
                    {
                        channel ! LogoutResponse(msg, Right(false))
                        log.debug("Did not find FacebookUser to {}", msg)
                    })
            } catch {
                case e =>
                    channel ! LogoutResponse(msg, Left(FacebookException("Could not logout Facebook user", e)))
                    log.error("Unexpected error processing {}, {}", msg, e)
            }


        case msg @ CreateFromCode(code, queryString) =>
            val me = context.self
            val channel = context.sender

            def error(e: Throwable) {
                channel ! CreateFromCodeResponse(msg, Left(FacebookException("Could not create Facebook service", e)))
                log.error("Error processing {}, {}", msg, e)
            }

            try {
                log.debug("Creating FacebookService using code {}", code)
                facebookAccess.getMe(code, queryString).onComplete(_.fold(
                    error(_),
                    _ match {
                        case GetMeResponse(_, Left(e)) => error(e)
                        case GetMeResponse(_, Right(fu)) =>
                            val facebookUser = updateMe(fu)
                            (me ? CreateFromId(facebookUser.id)).onComplete(_.fold(
                                error(_),
                                _ match {
                                    case CreateFromIdResponse(_, Left(e)) => error(e)
                                    case CreateFromIdResponse(_, Right(facebookService)) =>
                                        channel ! CreateFromCodeResponse(msg, Right(facebookService))
                                        log.debug("Created FacebookService with user {}", facebookUser)
                                }))
                    }))
            } catch { case e => error(e) }


        case msg @ CreateFromId(facebookUserId) =>
            val channel = context.sender

            try {
                cache.get(facebookUserId).cata(
                    fs => channel ! CreateFromIdResponse(msg, Right(fs)),
                    {
                        log.debug("Creating FacebookService using facebookUserId {}", facebookUserId)
                        Option(facebookUserDao.findById(facebookUserId)) match {
                            case Some(facebookUser) =>
                                channel ! CreateFromIdResponse(msg, Right(
                                    new FacebookServiceActorClient(context.actorOf(Props().withCreator {
                                        val fu = Option(facebookUserDao.findById(facebookUserId)).get
                                        new FacebookServiceActor(
                                            fu,
                                            facebookAccess,
                                            facebookUserDao,
                                            facebookPostDao,
                                            facebookFriendDao,
                                            echoClickUrl)
                                    }, facebookUserId))))
                                log.debug("Created Facebook service {}", facebookUserId)
                            case None =>
                                channel ! CreateFromIdResponse(msg, Left(FacebookUserNotFound(facebookUserId)))
                                log.debug("Did not find FacebookUser with id {}", facebookUserId)
                        }
                })
            } catch {
                case e =>
                    channel ! CreateFromIdResponse(msg, Left(FacebookException("Could not create Facebook service", e)))
                    log.error("Error processing {}, {}", msg, e)
            }


        case msg @ CreateFromFacebookId(facebookId, accessToken) =>
            val me = context.self
            val channel = context.sender

            def error(e: Throwable) {
                channel ! CreateFromFacebookIdResponse(msg, Left(FacebookException("Could not create Facebook service", e)))
                log.error("Error processing {}, {}", msg, e)
            }

            try {
                log.debug("Creating FacebookService using facebookId {}", facebookId)
                facebookAccess.fetchMe(accessToken).onComplete(_.fold(
                    error(_),
                    _ match {
                        case FetchMeResponse(_, Left(e)) => error(e)
                        case FetchMeResponse(_, Right(fu)) =>
                            val facebookUser = updateMe(fu)
                            (me ? CreateFromId(facebookUser.id)).onComplete(_.fold(
                                error(_),
                                _ match {
                                    case CreateFromIdResponse(_, Left(e)) => error(e)
                                    case CreateFromIdResponse(_, Right(facebookService)) =>
                                        channel ! CreateFromFacebookIdResponse(msg, Right(facebookService))
                                        log.debug("Created FacebookService from Facebook id {}", facebookId)
                                }))
                    }))
            } catch { case e => error(e) }
    }

}
