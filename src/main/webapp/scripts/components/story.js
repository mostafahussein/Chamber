define(
    [
        'jquery',
        'backbone',
        'underscore',
        'text!templates/story/story.html',
        'text!templates/story/login.html',
        'components/utils'
    ],
    function($, Backbone, _, templateStory, templateLogin, utils){
        return Backbone.View.extend({
            initialize: function(options){
                _.bindAll(this);
                this.el = options.el;
                this.element = $(this.el);
                this.properties = options.properties;
                this.EvAg = options.EvAg;
                if(this.properties.isWidget === true){
                    this.EvAg.bind("user/login", this.login);
                }
                this.EvAg.bind('story/show', this.load);
                this.EvAg.bind('user/login', this.login);
                this.EvAg.bind('story/hide', this.hide);
                this.EvAg.bind('field/show', this.hide);
                this.EvAg.bind('fade/click', this.close);
                this.locked = false;
            },
            events: {
                "click .echo-s-h-close" : "close",
                "click .comment-submit": "createComment",
                "click .login-button": "commentLogin",
                "click .echo-gallery-chapter" : "chapterClick",
                "click .echo-s-b-thumbnail": "imageClick",
                "click .echo-s-b-item": "nextImage",
                "click .story-nav-button": "navClick",
                "click .upvote": "upVote",
                "click .downvote": "downVote",
                "click #echo-story-from": "fromClick",
                "click #echo-story-gallery-next": "next",
                "click #echo-story-gallery-prev": "previous",
                "click .story-share": "share",
                "click #story-follow": "followClick",
                "click #story-login-container": "closeLogin",
                "click .story-login-button": "loginClick",
                "click .story-login-email": "loginClick",
                "click #comments-login": "showLogin",
                "click .story-link": "redirect"
            },
            followClick: function(ev){
                var self = this;
                var request = {};
                var currentTarget = $(ev.currentTarget);
                var followId = currentTarget.attr("echoedUserId");
                if(self.properties.echoedUser !== undefined ){
                    if(self.properties.echoedUser.id !== followId) {
                        if(self.following === false){
                            request = {
                                url: self.properties.urls.api + "/api/me/following/" + followId,
                                type: "PUT",
                                success: function(data){
                                    self.following = true;
                                    currentTarget.text("Unfollow").addClass("redButton").removeClass("greyButton");
                                }
                            }
                        } else {
                            request = {
                                url: self.properties.urls.api + "/api/me/following/" + followId,
                                type: "DELETE",
                                success: function(data){
                                    self.following = false;
                                    currentTarget.text("Follow").removeClass("redButton").addClass("greyButton");
                                }
                            }
                        }
                        utils.AjaxFactory(request)();
                    }
                } else{
                    self.showLogin();
                }
            },
            fromClick: function(ev){
                window.open(this.properties.urls.api + "/redirect/partner/" + this.data.story.partnerId);
            },
            loginClick: function(ev){
                var self = this;
                if(self.properties.isWidget){
                    var target = $(ev.currentTarget);
                    var href = target.attr('href');
                    window.open(href, "Echoed",'width=800,height=440,toolbar=0,menubar=0,location=0,status=1,scrollbars=0,resizable=0,left=0,top=0');
                    return false;
                }
            },
            showLogin: function(){
                var self = this;
                var login = $('<div id="story-login"></div>').html(templateLogin);
                $('#story-login-container').html(login);
                $('#story-logo-img').attr("src", self.properties.urls.images + "/logo_large.png");
                if(self.properties.isWidget){
                    $("#story-fb-login").attr("href", utils.getFacebookLoginUrl("redirect/close"));
                    $("#story-tw-login").attr("href", utils.getTwitterLoginUrl("redirect/close"));
                    $('#story-user-login').attr('href', self.properties.urls.api + "/" +utils.getLoginRedirectUrl("redirect/close"));
                    $('#story-user-signup').attr("href", self.properties.urls.api + "/" +utils.getSignUpRedirectUrl("redirect/close"));

                } else {
                    $("#story-fb-login").attr("href", utils.getFacebookLoginUrl(window.location.hash));
                    $("#story-tw-login").attr("href", utils.getTwitterLoginUrl(window.location.hash));
                    $('#story-user-login').attr('href', self.properties.urls.api + "/" + utils.getLoginRedirectUrl());
                    $('#story-user-signup').attr("href", self.properties.urls.api + "/" + utils.getSignUpRedirectUrl());


                }
                $('#story-login-container').fadeIn();
            },
            closeLogin: function(ev){
                var self = this;
                if($(ev.target).attr("id") === "story-login-container"){
                    $('#story-login-container').fadeOut();
                }
            },
            share: function(ev){
                var self = this;
                var target = $(ev.currentTarget);
                var href = "";
                switch(target.attr("type")){
                    case "fb":
                        href = "http://www.facebook.com/sharer/sharer.php?u=" + encodeURIComponent(self.properties.urls.api + "/graph/story/" + self.data.story.id)
                        break;
                    case "tw":
                        href = "http://twitter.com/intent/tweet?original_referer="
                            + encodeURIComponent(self.properties.urls.api + "/#story/" + self.data.story.id)
                            + "&url="
                            + encodeURIComponent(self.properties.urls.api + "/#story/" + self.data.story.id)
                            + "&via="
                            + "echoedinc";
                        break;
                    case "pinterest":
                        href = "http://pinterest.com/pin/create/button?url="
                            + encodeURIComponent(self.properties.urls.api + "/#story/" + self.data.story.id)
                            + "&media="
                            + encodeURIComponent(self.data.story.image.preferredUrl)
                            + "&description="
                            + encodeURIComponent(self.data.story.title);
                        break
                }
                window.open(href, "Share",'width=800,height=440,toolbar=0,menubar=0,location=0,status=1,scrollbars=0,resizable=0,left=0,top=0');
            },
            next: function(){
                var self = this;
                self.scroll(self.galleryNode.scrollTop() + self.galleryNode.height());
            },
            previous: function(){
                var self = this;
                self.scroll(self.galleryNode.scrollTop() - self.galleryNode.height());
            },
            scroll: function(position){
                var self = this;
                self.galleryNode.animate({
                    scrollTop: position
                });
            },
            upVote: function(ev){
                var self = this;
                var target = $(ev.currentTarget);
                if(self.properties.echoedUser !== undefined){
                    utils.AjaxFactory({
                        url: self.properties.urls.api + "/api/upvote",
                        data: {
                            storyId: self.data.story.id,
                            storyOwnerId: self.data.echoedUser.id
                        },
                        success: function(data){
                            self.data.votes[self.properties.echoedUser.id] = {
                                echoedUserId: self.properties.echoedUser.id,
                                value: 1
                            };
                            self.renderVotes();
                        }
                    })();
                } else{
                    self.showLogin();
                }
            },
            downVote: function(ev){
                var self = this;
                var target = $(ev.currentTarget);
                if(self.properties.echoedUser !== undefined){
                    utils.AjaxFactory({
                        url: self.properties.urls.api + "/api/downvote",
                        data: {
                            storyId: self.data.story.id,
                            storyOwnerId: self.data.echoedUser.id
                        },
                        success: function(data){
                            self.data.votes[self.properties.echoedUser.id] = {
                                echoedUserId: self.properties.echoedUser.id,
                                value: -1
                            };
                            self.renderVotes();
                        }
                    })();
                }
                else {
                    self.showLogin();
                }
            },
            login: function(echoedUser){
                var self = this;
                this.properties.echoedUser = echoedUser;
                this.element.find('.comment-login').fadeOut(function(){
                    self.element.find('.comment-submit').fadeIn();
                });
                $('#story-login-container').fadeOut();
                self.renderVotes();
                self.renderFollowing();
            },
            navClick: function(ev){
                var self = this;
                var target = $(ev.currentTarget);
                var action = target.attr("act");
                self.EvAg.trigger('exhibit/story/'+ action, self.data.story.id);
            },
            load: function(id){
                var self = this;
                utils.AjaxFactory({
                    url: self.properties.urls.api + "/api/story/" + id,
                    success: function(data){
                        self.data = data;
                        self.EvAg.trigger('pagetitle/update', data.story.title);
                        self.render();
                    }
                })();
            },
            renderViews: function(){
                var self = this;
                $('#story-views').text("Views: " + self.data.story.views);
            },
            renderVotes: function(){
                var self = this;
                var upVotes = 0, downVotes = 0, key;
                var votes = self.data.votes;
                self.element.find('.upvote').removeClass('on');
                self.element.find('.downvote').removeClass('on');
                for (key in votes){
                    if(votes[key].value > 0 ) upVotes++;
                    else if(votes[key].value < 0) downVotes++;
                }
                $('#upvote-counter').text(upVotes);
                $('#downvote-counter').text(downVotes);

                if(self.properties.echoedUser !== undefined){
                    var vote = self.data.votes[self.properties.echoedUser.id];
                    if(vote !== undefined){
                        if(vote.value > 0) self.element.find('.upvote').addClass('on');
                        else if(vote.value < 0) self.element.find('.downvote').addClass('on');
                    }
                }
            },
            renderFollowing: function(){
                var self = this;
                self.following = false;
                $('#story-follow').attr("echoedUserId", self.data.echoedUserId);
                if(self.properties.echoedUser !== undefined){
                    if(self.properties.echoedUser.id !== self.data.echoedUser.id){
                        utils.AjaxFactory({
                            url: self.properties.urls.api + "/api/me/following",
                            success: function(data){

                                $.each(data, function(index, following){
                                    if(following.echoedUserId === self.data.echoedUser.id ) self.following = true;
                                });
                                if(self.following === true){
                                    $('#story-follow').text("Unfollow").removeClass("greyButton").addClass("redButton").fadeIn();
                                } else {
                                    $('#story-follow').addClass('greyButton').text("Follow").fadeIn();
                                }
                            }
                        })();
                    } else{
                        $('#story-follow').fadeOut();
                    }
                } else {
                    $('#story-follow').addClass('greyButton').text("Follow").fadeIn();
                }
            },
            render: function(){

                var self = this;
                var template = _.template(templateStory, self.data);
                self.element.html(template);
                self.chapters = {
                    array: [],
                    hash: {}
                };

                $.each(self.data.chapters, function(index,chapter){
                    var hash = {
                        chapter: chapter,
                        images: []
                    };
                    if(index === 0 && self.data.story.image){
                        hash.images.push(self.data.story.image);
                    }
                    $.each(self.data.chapterImages, function(index, chapterImage){
                        if(chapterImage.chapterId === chapter.id) {
                            hash.images.push(chapterImage.image);
                        }
                    });
                    self.chapters.array.push(hash);
                    self.chapters.hash[chapter.id] = index;
                });
                self.currentChapterIndex = 0;
                self.currentImageIndex = 0;

                self.text = self.element.find('.echo-s-b-text');
                if(self.properties.echoedUser !== undefined){
                    if(self.data.echoedUser.id === self.properties.echoedUser.id){
                        var header = self.element.find('.echo-story-header');
                        $('#story-edit-tab').attr("href","#write/story/" + self.data.story.id).css("display","inline-block");
                    }
                }

                self.gallery = $('#echo-s-b-gallery');

                $('#echo-s-h-t-n-i').attr("src", utils.getProfilePhotoUrl(self.data.echoedUser, self.properties.urls));
                $('#story-follow').attr("echoedUserId", self.data.echoedUser.id);
                if(self.properties.isWidget) $('#story-user-link').attr("href", self.properties.urls.api + "#user/" + self.data.echoedUser.id).attr('target',"_blank");
                else $('#story-user-link').attr("href", self.properties.urls.api + "#user/" + self.data.echoedUser.id)

                if(self.properties.isWidget !== true && self.data.story.productInfo !== null){
                    var from = $('#echo-story-from');
                    var fromText = $('#echo-story-from-text');
                    if(self.data.story.partnerHandle !== "Echoed"){
                        var p = self.data.story.partnerHandle ? self.data.story.partnerHandle : self.data.story.partnerId;
                        fromText.text(self.data.story.productInfo);
                        from.show();
                    }
                }

                self.itemNode = $("<div class='echo-s-b-item'></div>");
                self.itemImageContainer = $("<div class='echo-s-b-i-c'></div>");
                self.img = $("<img />");
                self.itemNode.append(self.itemImageContainer.append(self.img)).appendTo(self.gallery);
                self.galleryNode = $("#echo-story-gallery");
                self.galleryNodeBody = $('#echo-story-gallery-body');
                self.chapterText = $("<div class='echo-s-b-t-b'></div>")
                self.text.append(self.chapterText);
                self.renderGalleryNav();
                self.renderComments();
                self.renderChapter();
                self.renderVotes();
                self.renderFollowing();
                self.renderViews();
                self.scroll(0);

                self.EvAg.trigger('fade/show');
                self.element.css({
                    "margin-left": -(self.element.width() / 2)
                });
                self.element.fadeIn();
            },
            renderGalleryNav: function(){
                var self = this;
                self.thumbnails = {};
                self.titles = [];
                self.galleryChapters = [];
                $.each(self.chapters.array, function(index, chapter){
                    self.galleryChapters[index]=  $('<div></div>').addClass('echo-gallery-chapter').attr("index", index);
                    var title = $('<div></div>').addClass('echo-gallery-title').text(chapter.chapter.title);
                    self.galleryChapters[index].append(title);
                    self.galleryNodeBody.append(self.galleryChapters[index]);
                    $.each(chapter.images, function(index2, image){
                        var thumbNailHash = index + "-" + index2;
                        self.thumbnails[thumbNailHash] = $('<img />').addClass("echo-s-b-thumbnail").attr("index", thumbNailHash).attr("src", image.preferredUrl).css(utils.getImageSizing(image, 90));
                        self.galleryChapters[index].append(self.thumbnails[thumbNailHash]);
                    });
                });
            },
            nextImage: function(){
                var self = this;
                self.currentImageIndex++;
                if(self.currentImageIndex >= self.chapters.array[self.currentChapterIndex].images.length){
                    self.nextChapter();
                } else {
                    self.renderImage(self.currentImageIndex);
                }
            },
            nextChapter: function(){
                var self = this;
                self.currentChapterIndex++;
                self.currentImageIndex = 0;
                if(self.currentChapterIndex >= self.chapters.array.length){
                    self.currentChapterIndex = 0;
                }
                self.renderChapter(self.currentChapterIndex);
            },
            imageClick: function(e){
                var self = this;
                var index = $(e.target).attr("index");
                self.currentChapterIndex = index.split("-")[0];
                self.currentImageIndex = index.split("-")[1];
                self.renderChapter();
            },
            chapterClick: function(e){
                var self = this;
                var target = $(e.target);
                if(!target.is("img")){
                    self.currentChapterIndex = $(e.currentTarget).attr("index");
                    self.currentImageIndex = 0;
                    self.renderChapter();
                }
            },
            renderChapter: function(){
                var self = this;
                var textArea = self.element.find('.echo-s-b-text');
                var chapterText = self.chapters.array[self.currentChapterIndex].chapter.text;

                if (chapterText.length < 300) this.chapterType = 'photo';
                else this.chapterType = 'text';

                textArea.fadeOut(function(){
                    self.element.find('.echo-story-chapter-title').text(self.chapters.array[self.currentChapterIndex].chapter.title);
                    self.chapterText.html(utils.replaceUrlsWithLink(utils.escapeHtml(self.chapters.array[self.currentChapterIndex].chapter.text)).replace(/\n/g, '<br />'));
                    textArea.fadeIn();
                });

                self.galleryNode.find('.echo-gallery-chapter').removeClass("highlight");
                self.galleryChapters[self.currentChapterIndex].addClass("highlight");
                self.scroll(self.galleryNode.scrollTop() + self.galleryChapters[self.currentChapterIndex].position().top);
                self.renderImage();
            },
            renderImage: function(){
                var self = this;
                var currentImage = self.chapters.array[self.currentChapterIndex].images[self.currentImageIndex];
                var imageSizing = {};

                if(currentImage !== null && currentImage !== undefined){
                    if(this.chapterType === 'photo') {
                        imageSizing = utils.getMaxImageSizing(currentImage, 842, 700)
                        self.gallery.addClass("gallery-photo");
                        self.gallery.removeClass('gallery-text');
                        self.chapterText.addClass('caption')
                    } else {
                        imageSizing = utils.getImageSizing(currentImage, 450);
                        self.gallery.addClass("gallery-text");
                        self.gallery.removeClass('gallery-photo');
                        self.chapterText.removeClass('caption')
                    }

                    self.gallery.show();
                    self.img.fadeOut(function(){
                        self.itemImageContainer.animate(
                            imageSizing,
                            'fast',
                            function(){
                                if(currentImage.storyUrl !== null){
                                    self.img.css(imageSizing).attr('src', currentImage.storyUrl).fadeIn();
                                } else {
                                    self.img.css(imageSizing).attr('src', currentImage.originalUrl).fadeIn();
                                }
                                self.img.attr('src', currentImage.originalUrl).fadeIn();
                                self.galleryNode.find('.echo-s-b-thumbnail').removeClass("highlight");
                                self.thumbnails[self.currentChapterIndex + "-" + self.currentImageIndex].addClass("highlight");
                            });
                    });

                } else{
                    self.gallery.addClass("gallery-text");
                    self.gallery.removeClass('gallery-photo');
                    self.chapterText.removeClass('caption')
                    self.gallery.hide();
                }
            },
            renderComments: function(){
                var self = this;
                var commentListNode = self.element.find('.echo-s-c-l');
                var comments = {
                    children: []
                };
                $.each(self.data.comments, function(index, comment){
                    var parentId = comment.parentCommentId;
                    if(parentId == null){
                        comments.children.push(comment);
                    } else {
                        if(comments[parentId] == null){
                            comments[parentId] = {
                                children: []
                            }
                        }
                        comments[parentId].children.push(comment);
                    }
                });
                commentListNode.empty();
                $("#echo-story-comment-ta").val("");


                $.each(self.data.comments, function(index,comment){
                    var elapsedString = utils.timeElapsedString(utils.timeStampStringToDate(comment.createdOn.toString()));
                    var elapsedNode = $('<span class="echo-s-c-l-c-d"></span>').append(elapsedString);
                    var commentUserNode = $('<div class="echo-s-c-l-c-u"></div>').append($("<a class='story-link red-link'></a>").text(comment.echoedUser.name).attr("href","#user/" + comment.echoedUser.id)).append(elapsedNode);
                    var img = $('<img class="echo-s-c-l-c-u-i" />').attr("src", utils.getProfilePhotoUrl(comment.echoedUser, self.properties.urls)).attr("align", "absmiddle");
                    img.prependTo(commentUserNode);
                    var commentText = $('<div class="echo-s-c-l-c-t"></div>').html(utils.replaceUrlsWithLink(utils.escapeHtml(comment.text).replace(/\n/g, '<br />')));
                    var commentNode = $('<div class="echo-s-c-l-c"></div>').append(commentUserNode).append(commentText);
                    commentListNode.append(commentNode);
                });
                $('#echo-s-c-t-count').text("(" + self.data.comments.length + ")");
                if(Echoed.echoedUser) {
                    self.element.find('.comment-submit').fadeIn();
                } else{
                    self.element.find('.comment-login-fb').attr("href", utils.getFacebookLoginUrl("redirect/close"));
                    self.element.find('.comment-login-tw').attr("href", utils.getTwitterLoginUrl("redirect/close"));
                    self.element.find('.comment-login').fadeIn();
                }
            },
            commentLogin: function(ev){
                var href = $(ev.currentTarget).attr("href");
                var child = window.open("about:blank", "Echoed",'width=800,height=440,toolbar=0,menubar=0,location=0,status=1,scrollbars=0,resizable=0,left=0,top=0');
                child.location = href;
            },
            createComment: function(){
                var self = this;
                var storyId = self.data.story.id;
                var chapterId = self.data.chapters[0].id;
                var text = $.trim($("#echo-story-comment-ta").val());
                if(text === ""){
                    alert("Please enter in a comment");
                } else if(self.locked !== true){
                    self.locked = true;
                    utils.AjaxFactory({
                        url: self.properties.urls.api + "/story/" + storyId + "/chapter/" + chapterId + "/comment",
                        type: "POST",
                        data: {
                            text: text,
                            storyOwnerId: self.data.echoedUser.id
                        },
                        success: function(createCommentData) {
                            self.locked = false;
                            self.data.comments.push(createCommentData);
                            self.renderComments();
                        }
                    })();
                }
            },
            hide: function(){
                this.element.fadeOut();
                this.element.empty();
            },
            redirect: function(){
                var self = this;
                this.element.fadeOut(function(){
                    self.element.empty();
                })
                this.EvAg.trigger('fade/hide');
            },
            close: function(){
                this.element.fadeOut();
                this.EvAg.trigger("fade/hide");
                this.EvAg.trigger("hash/reset");
            }
        });

    }
);
