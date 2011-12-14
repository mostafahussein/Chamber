package com.echoed.chamber.services.partneruser

import collection.mutable.WeakHashMap
import reflect.BeanProperty
import org.slf4j.LoggerFactory
import com.echoed.chamber.domain.RetailerUser
import scalaz._
import Scalaz._
import akka.actor.{Channel, Actor}


class PartnerUserServiceLocatorActor extends Actor {

    private val logger = LoggerFactory.getLogger(classOf[PartnerUserServiceLocatorActor])

    @BeanProperty var partnerUserServiceCreator: PartnerUserServiceCreator = _

    private val cache = WeakHashMap[String, PartnerUserService]()
    private val cacheById = WeakHashMap[String, PartnerUserService]()

    def login(msg: Login, channel: Channel[LoginResponse]) {
        val email = msg.email
        val password = msg.password
        logger.debug("Locating PartnerUserService for {}", email)

        cache.get(email) match {
            case Some(partnerUserService) =>
                logger.debug("Cache hit for {}", email)
                partnerUserService.getPartnerUser.onResult {
                    case GetPartnerUserResponse(_, Right(pu)) =>
                        if (pu.isPassword(password)) {
                            cacheById(pu.id) = partnerUserService
                            channel ! LoginResponse(msg, Right(partnerUserService))
                            logger.debug("Valid login for {}", email)
                        } else {
                            channel ! LoginResponse(msg, Left(LoginError("Invalid login")))
                            logger.debug("Invalid login for {}", email)
                        }
                    case GetPartnerUserResponse(_, Left(e)) =>
                        channel ! LoginResponse(msg, Left(LoginError("Invalid login", e)))
                        logger.error("Error getting PartnerUser from PartneruserService for {}", email)
                }

            case None =>
                logger.debug("Cache miss for {}", email)
                partnerUserServiceCreator.createPartnerUserService(email).onResult {
                    case CreatePartnerUserServiceResponse(_, Right(pus)) =>
                        cache(email) = pus
                        logger.debug("Seeded cache for {}", email)
                        login(msg, channel)
                    case CreatePartnerUserServiceResponse(_, Left(e)) =>
                        channel ! LoginResponse(msg, Left(LoginError("Invalid login", e)))
                        logger.debug("Invalid login for {}", email)
                }
        }
    }

    def receive = {
        case msg: Login => login(msg, self.channel)
        case msg @ Locate(partnerUserId) =>
            self.channel ! LocateResponse(msg, cacheById.get(partnerUserId).toRight(LoginError("No partner user")))
    }


}
