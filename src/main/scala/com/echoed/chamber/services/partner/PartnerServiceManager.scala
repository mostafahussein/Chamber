package com.echoed.chamber.services.partner

import akka.dispatch.Future
import com.echoed.chamber.domain.partner.{PartnerSettings, PartnerUser, Partner}


trait PartnerServiceManager {
    
    def registerPartner(
            partner: Partner,
            partnerSettings: PartnerSettings,
            partnerUser: PartnerUser): Future[RegisterPartnerResponse]

    def locatePartnerService(partnerId: String): Future[LocateResponse]

    def locatePartnerByDomain(domain: String): Future[LocateByDomainResponse]

    def locatePartnerByEchoId(echoId: String): Future[LocateByEchoIdResponse]

    def getView(partnerId: String): Future[GetViewResponse]

    def recordEchoStep(
            echoId: String,
            step: String,
            echoedUserId: Option[String] = None,
            echoClickId: Option[String] = None): Future[RecordEchoStepResponse]

    def requestEcho(
            partnerId: String,
            request: String,
            browserId: String,
            ipAddress: String,
            userAgent: String,
            referrerUrl: String,
            echoedUserId: Option[String] = None,
            echoClickId: Option[String] = None,
            view: Option[String] = None): Future[RequestEchoResponse]

}

