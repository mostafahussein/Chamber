package com.echoed.chamber.domain

import java.util.Date
import com.echoed.util.DateUtils._
import org.squeryl.KeyedEntity
import com.echoed.util.AsMap

trait DomainObject extends Identifiable with AsMap with KeyedEntity[String] {

    def updatedOn: Long
    def createdOn: Long

    def updatedOnDate: Option[Date] = updatedOn
    def createdOnDate: Option[Date] = createdOn

    def asPublicMap = asMap - ("createdOn", "updatedOn", "_isPersisted")

    def isUpdated = updatedOn > createdOn
}
