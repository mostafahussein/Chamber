package com.echoed.chamber.dao

import com.echoed.chamber.domain.Echo

import java.util.{List => JList}
import org.apache.ibatis.annotations.Param

trait EchoDao {

    def insert(echo: Echo): Int

    def updateForStep(echo: Echo): Int

    def updateForEcho(echo: Echo): Int

    def updateFacebookPostId(echo: Echo): Int

    def updateTwitterStatusId(echo: Echo): Int

    def findByIdOrPostId(id: String): Echo

    def findByEchoPossibilityId(echoPossibilityId: String): Echo

    @deprecated(message = "This is to support the old integration method")
    def findByIdOrEchoPossibilityId(id: String): Echo

    def deleteByEchoPossibilityId(echoPossibilityId: String): Int

    def findById(id: String): Echo

    def findByIdAndEchoedUserId(
        @Param("id") id: String,
        @Param("echoedUserId") echoedUserId: String): Echo

    def findByPartnerId(partnerId: String): JList[Echo]

    def deleteByPartnerId(partnerId: String): Int

    def selectCount: Long

    def deleteById(id: String): Int

}
