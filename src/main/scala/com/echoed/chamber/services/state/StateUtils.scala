package com.echoed.chamber.services.state

import com.echoed.chamber.domain.{Topic, StoryState, Story, Vote}
import org.squeryl.PrimitiveTypeMode._
import com.echoed.chamber.services.state.schema.ChamberSchema._
import collection.mutable.{Map => MMap}

private[state] object StateUtils {

    def readStory(s: Story, echo: Option[schema.Echo] = None, topic: Option[Topic] = None) = {
        val c = from(chapters)(c => where(c.storyId === s.id) select(c)).toList
        val ci = from(chapterImages)(ci => where(ci.storyId === s.id) select(ci)).map { ci =>
            images.lookup(ci.imageId).map(img => ci.copy(image = img.convertTo))
        }.filter(_.isDefined).map(_.get).toList

        val cm = from(comments)(cm => where(cm.storyId === s.id) select(cm) orderBy(cm.createdOn.asc)).toList.map { cm =>
            echoedUsers.lookup(cm.byEchoedUserId).map(eu => cm.copy(echoedUser = eu)).get
        }.toList
        val lk = from(links)(lk => where(lk.storyId === s.id) select(lk) orderBy(lk.createdOn.asc)).toList

        val eu = echoedUsers.lookup(s.echoedUserId).get
        val img = Option(s.imageId).map(images.lookup(_).get.convertTo).orElse(None)

        val p = partners.lookup(s.partnerId).get
        val ps = partnerSettings.lookup(s.partnerSettingsId).get
        val e = echo.orElse(Option(s.echoId).flatMap(echoes.lookup(_)))
        val m = from(moderations)(m => where(m.refId === s.id) select(m)).toList
        val v = Map.empty ++ from(votes)(v => where(v.ref === "Story" and v.refId === s.id) select(v))
                    .toList
                    .foldLeft(MMap.empty[String, Vote])((map, vote) => map + (vote.echoedUserId -> vote))

        val t = Option(s.topicId).map { id => from(topics)(t => where(t.id === id) select(t)).head }.orElse(topic)


        StoryState(
                s.id,
                s.updatedOn,
                s.createdOn,
                s.title,
                s.views,
                s.community,
                eu,
                s.imageId,
                img,
                c,
                ci,
                cm,
                lk,
                p,
                ps,
                e.map(_.convertTo(img.get)),
                m,
                v,
                t,
                s.contentType,
                s.contentPath,
                s.contentPageTitle)
    }
}

