package com.echoed.chamber.services.partneruser

import akka.actor.Actor
import org.slf4j.LoggerFactory
import com.echoed.chamber.domain._
import scala.collection.mutable.{Map => MMap, ListBuffer => MList}
import com.echoed.chamber.dao.RetailerUserDao
import com.echoed.chamber.dao.views.RetailerViewDao


class PartnerUserServiceActor(
        partnerUser: RetailerUser,
        partnerUserDao: RetailerUserDao,
        retailerViewDao: RetailerViewDao) extends Actor {

    private final val logger = LoggerFactory.getLogger(classOf[PartnerUserServiceActor])


    def receive = {
        case msg: GetPartnerUser =>
            self.channel ! GetPartnerUserResponse(msg, Right(partnerUser))

        case msg: GetRetailerSocialSummary =>
            self.channel ! GetRetailerSocialSummaryResponse(msg,Right(retailerViewDao.getSocialActivityByRetailerId(partnerUser.id)))
    }
    
}
