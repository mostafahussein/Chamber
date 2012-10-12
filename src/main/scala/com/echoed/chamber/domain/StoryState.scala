package com.echoed.chamber.domain

import com.echoed.chamber.domain.partner.{PartnerSettings, Partner}
import com.echoed.chamber.domain.views.StoryFull
import scala.collection.JavaConversions._
import com.echoed.util.UUID
import com.echoed.util.DateUtils._
import java.util.Date

case class StoryState(
        id: String,
        updatedOn: Long,
        createdOn: Long,
        title: String,
        productInfo: String,
        views: Int,
        community: String,
        echoedUser: EchoedUser,
        imageId: String,
        image: Option[Image],
        chapters: List[Chapter],
        chapterImages: List[ChapterImage],
        comments: List[Comment],
        partner: Partner,
        partnerSettings: PartnerSettings,
        echo: Option[Echo],
        moderations: List[Moderation],
        votes: Map[String, Vote]) extends DomainObject {

    def this(
            eu: EchoedUser,
            p: Partner,
            ps: PartnerSettings,
            e: Option[Echo] = None,
            img: Option[Image] = None) = this(
        null,
        0L,
        0L,
        null,
        null,
        0,
        p.category,
        eu,
        img.map(_.id).orNull,
        img,
        List.empty[Chapter],
        List.empty[ChapterImage],
        List.empty[Comment],
        p,
        ps,
        e,
        List.empty[Moderation],
        Map.empty[String, Vote])

    def isCreated = id != null && createdOn > 0
    def create(title: String, productInfo: String, community: String, imageId: String) = {
        val storyState = copy(
            id = UUID(),
            updatedOn = new Date,
            createdOn = new Date,
            title = title,
            productInfo = productInfo,
            community = community,
            imageId = imageId)
        if (partnerSettings.moderateAll) storyState.moderate(partner.name, "Partner", partner.id)
        else storyState
    }

    def asStory = Story(
            id,
            updatedOn,
            createdOn,
            echoedUser.id,
            partner.id,
            partner.handle,
            partnerSettings.id,
            image.map(_.id).orNull,
            image.orNull,
            title,
            echo.map(_.id).orNull,
            echo.map(_.productId).orNull,
            productInfo,
            views,
            numComments,
            upVotes,
            downVotes,
            community)

    def asStoryInfo = StoryInfo(echoedUser, echo.orNull, partner, partnerSettings.makeStoryPrompts, asStoryFull.orNull)
    def asStoryFull =
            if (!isCreated) None
            else Option(StoryFull(id, asStory, echoedUser, chapters, chapterImages, comments, votes, moderationDescription))

    private def selfModeratedPredicate: Moderation => Boolean = _.moderatedRef == "EchoedUser"
    private def echoedModeratedPredicate: Moderation => Boolean = _.moderatedRef == "AdminUser"
    private def partnerModeratedPredicate: Moderation => Boolean = _.moderatedRef == "PartnerUser"

    val isPublished: Boolean = chapters.foldLeft(false)((published, chapter) => published || chapter.isPublished)
    val isEchoedModerated = moderated(echoedModeratedPredicate)
    val isModerated = moderated(partnerModeratedPredicate)
    val isSelfModerated = moderated(selfModeratedPredicate)

    val numComments = comments.length
    val upVotes = votes.filter(t => t._2.value > 0).size
    val downVotes = votes.filter(t => t._2.value < 0).size

    def moderate(moderatedBy: String, moderatedRef: String, moderatedRefId: String, moderated: Boolean = true) =
        copy(moderations = new Moderation(
            "Story",
            this.id,
            moderatedBy,
            moderatedRef,
            moderatedRefId,
            moderated) :: moderations)


    val moderationDescription = new ModerationDescription(
        findModeration(partnerModeratedPredicate),
        findModeration(echoedModeratedPredicate),
        findModeration(selfModeratedPredicate))

    private def moderated(predicate: Moderation => Boolean) =
            findModeration(predicate).map(_.moderated).getOrElse(false)

    private def findModeration(predicate: Moderation => Boolean) =
            moderations.sortWith(_.createdOn > _.createdOn).filter(predicate).headOption

}