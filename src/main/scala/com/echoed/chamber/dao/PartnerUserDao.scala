package com.echoed.chamber.dao

import com.echoed.chamber.domain.PartnerUser


trait PartnerUserDao {

    def updatePassword(partnerUser: PartnerUser): Int

    def findById(id: String): PartnerUser

    def insert(partnerUser: PartnerUser): Int

    def deleteById(id: String): Int

    def findByEmail(email: String): PartnerUser

    def deleteByEmail(email: String): Int

}