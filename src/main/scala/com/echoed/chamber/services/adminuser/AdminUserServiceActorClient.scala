package com.echoed.chamber.services.adminuser

import akka.actor.ActorRef
import com.echoed.chamber.services.ActorClient
import com.echoed.chamber.domain.partner.PartnerSettings

class AdminUserServiceActorClient(adminUserServiceActor: ActorRef) extends AdminUserService with ActorClient {

    def actorRef = adminUserServiceActor
    
    def getUsers =
        (adminUserServiceActor ? GetUsers()).mapTo[GetUsersResponse]

    def getPartners =
        (adminUserServiceActor ? GetPartners()).mapTo[GetPartnersResponse]

    def getPartnerSettings(partnerId: String) =
        (adminUserServiceActor ? GetPartnerSettings(partnerId)).mapTo[GetPartnerSettingsResponse]
    
    def getEchoPossibilities =
        (adminUserServiceActor ? GetEchoPossibilities()).mapTo[GetEchoPossibilitesResponse]

    def getAdminUser =
        (adminUserServiceActor ? GetAdminUser()).mapTo[GetAdminUserResponse]

    def updatePartnerSettings(partnerSettings: PartnerSettings) =
        (adminUserServiceActor ? UpdatePartnerSettings(partnerSettings)).mapTo[UpdatePartnerSettingsResponse]

    def logout(adminUserId: String) =
        (adminUserServiceActor ? Logout(adminUserId)).mapTo[LogoutResponse]

    val id = actorRef.id

    override def toString = id

}
