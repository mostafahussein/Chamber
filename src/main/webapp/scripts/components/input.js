    define(
    [
        'jquery',
        'backbone',
        'underscore',
        'expanding',
        'components/utils',
        'components/select',
        'models/story',
        'hgn!templates/input/storySummary',
        'hgn!templates/input/storyCoverInput',
        'hgn!templates/input/storyCover',
        'hgn!templates/input/storyChapter',
        'hgn!templates/input/storyChapterInput',
        'cloudinary'
    ],
    function($, Backbone, _, expanding, utils, Select, ModelStory, templateSummary, templateStoryCoverInput, templateStoryCover, templateChapter, templateChapterInput){
        return Backbone.View.extend({
            initialize: function(options){
                _.bindAll(this);
                this.element =      $(options.el);
                this.properties =   options.properties;
                this.modelUser =    options.modelUser;
                this.modelContext = options.modelContext;
                this.EvAg =         options.EvAg;
                this.modelUser.on("change:id",  this.login);
                this.EvAg.bind("input:write",   this.write);
                this.EvAg.bind("input:edit",    this.edit);
                this.locked =               false;
                this.cloudName =            "";

                this.currentLinks = [];
            },
            events: {
                "click .field-close" :         "close",
                "click #edit-cover":           "editStoryClick",
                "click .edit-chapter" :        "editChapterClick",
                'click #submit-cover':         "submitCover",
                'click #chapter-publish':      "publishChapterClick",
                'click #chapter-save':         'saveChapterClick',
                'click #chapter-add':          'addChapterClick',
                'click #story-finish':         'finishStoryClick',
                'click #story-hide':           "hideStoryClick",
                'click #chapter-cancel':       'cancelChapterClick',
                'click .chapter-thumb-x':      'removeChapterThumb',
                'click .fade':                 "fadeClick",
                'click .text':                 "textClick",
                "click .photos":               "photosClick",
                "click .link":                 "linkClick",
                "click .subtit":               "subtitleClick",
                "focusout #link-url":          'postLink',
                "focusout #link-description":  'addLinkDescription'
            },
            fadeClick: function(ev){
                if($(ev.target).hasClass("fade")){
                    this.close();
                }
            },
            linkClick: function(){
                $("#input-link").toggle();
                $(".link").toggleClass("on");
            },
            photosClick: function(){
                $("#input-photos").toggle();
                $(".photos").toggleClass("on");
            },
            textClick: function(){
                $("#input-text").toggle();
                $(".text").toggleClass("on");
            },
            subtitleClick: function(){
                $("#input-subtitle").toggle();
                $(".subtit").toggleClass("on");
            },
            login: function(){
                if(this.loaded === true) this.load();
            },
            edit: function(id){
                this.id = id;
                this.type = "story";
                this.load();
            },
            write: function(){
                this.id = this.modelContext.id;
                this.type = this.modelContext.get("contextType");
                this.load();
            },
            load: function(){
                var self = this;
                var loadData = {};
                loadData[this.type + "Id"] = this.id;
                loadData["contentPath"] = this.modelContext.getPage();
                loadData["contentType"] = this.modelContext.getContentTypeSinguilar();
                loadData["contentPageTitle"] = this.modelContext.getPageTitle();

                if(this.modelUser.isLoggedIn()){
                    this.modelStory = new ModelStory(null, {
                        loadData: loadData,
                        properties: this.properties,
                        success: function(model){
                            self.render();
                        }
                    });
                } else {
                    this.EvAg.trigger("login/init", self.load, "", self.close);
                }
            },
            unload: function(callback){
                var self = this;
                self.element.fadeOut(function(){
                    callback();
                    self.element.empty();
                    self.data = {};
                });
            },
            render: function(){
                var self = this;
                self.element.empty();
                self.locked = false;
                self.template = templateSummary({ context: this.modelContext.toJSON()});
                self.element.html(self.template);

                self.cover = $('#field-summary-cover');
                self.body = $('#story-summary-body');

                if(this.modelStory.get("isNew")){
                    self.loadChapterInputTemplate({});
                } else if(this.modelStory.get("chapters").length > 0){
                    self.loadChapterTemplates();
                } else {
                    self.loadChapterInputTemplate({});
                }
                self.show();
            },
            removeChapterThumb: function(e){
                var target = $(e.currentTarget).parent();
                var id = $(e.currentTarget).parent().attr("imageId");
                for(var i = 0; i < this.currentImages.length; i++){
                    if(this.currentImages[i].id === id) this.currentImages.splice(i, 1);
                }
                target.fadeOut(function(){$(this).remove()});
            },
            editStoryClick: function(){
                this.loadStoryInputTemplate();
            },
            editChapterClick: function(ev){
                var chapterIndex = $(ev.currentTarget).attr('chapterIndex');
                this.loadChapterInputTemplate({ index: chapterIndex })
            },
            loadStoryCoverTemplate: function(){
                var story = this.modelStory.get("story");
                var template = templateStoryCover(this.modelStory.get("story"));
                this.cover.html(template);
                if (story.image !== null) {
                    utils.scaleByHeight(story.image, 50)
                            .addClass("story-summary-photo")
                            .appendTo(this.cover.find('.story-input-photo'));
                } else this.cover.find('.story-input-photo-row').hide();
            },
            loadChapterInputTemplate: function(option){
                var self = this;

                var cElement = function(opt){
                    if(opt.index) return $("#chapter-row-" + opt.index);
                    else return $('<div class="field-main-row clearfix"></div>').appendTo(self.body);
                }(option);

                var chapter =           this.modelStory.getChapter(option.index);
                var chapterImages =     this.modelStory.getChapterImages(chapter);
                this.currentImages =    [];
                this.editChapterId =    chapter.id;

                cElement.fadeOut(function(){
                    var template =      templateChapterInput({ chapter: chapter });
                    $(this).html(template);
                    if(self.modelStory.get("chapterImages").length) self.photosClick();
                    if(chapter.title) self.subtitleClick();
                    self.textClick();


                    var chapterPhotos =         $('#story-input-thumbnails');
                    var placeholder =           $('#thumbnail-placeholder');
                    if(chapterImages.length)    $('#input-photos').show();

                    $.each(chapterImages, function(index, chapterImage){
                        var thumbDiv =  $('<div></div>').addClass("thumb").addClass('chapter-thumb').attr("index", index).attr("imageId",chapterImage.image.id);
                        var thumbX =    $('<div></div>').addClass('chapter-thumb-x');
                        thumbDiv.append(thumbX);
                        var photo = utils.scaleByHeight(chapterImage.image, 75);
                        placeholder.before(thumbDiv.append(photo));
                        self.currentImages.push(chapterImage.image);
                    });

                    $("#chapter-text").expandingTextarea();

                    $('#photo-upload-button').cloudinary_fileupload({
                        dragover: function(e){
                            var dropZone = $('#thumb-placeholder'),
                                timeout = window.dropZoneTimeout;
                            if (!timeout) {
                                dropZone.addClass('in');
                            } else {
                                clearTimeout(timeout);
                            }
                            if (e.target === dropZone[0]) {
                                dropZone.addClass('hover');
                            } else {
                                dropZone.removeClass('hover');
                            }
                            window.dropZoneTimeout = setTimeout(function () {
                                window.dropZoneTimeout = null;
                                dropZone.removeClass('in hover');
                            }, 100);
                        },
                        progress: function(e,data){
                            var pct = data.loaded / data.total * 100;
                            $('#photo-upload-progress-fill').css({
                                width: pct + "%"
                            });
                        },
                        submit: function(e, data) {
                            $('#photo-upload-progress').show();

                            var storyId = self.modelStory.id;
                            var url = "/story/" + storyId + "/image";
                            var e = $(this);
                            $.ajax({
                                url: url,
                                type: "POST",
                                dataType: "json",
                                success: function(result) {
                                    e.fileupload('option', 'url', result.uploadUrl);
                                    data.formData = result;
                                    self.cloudName = result.cloudName;
                                    e.fileupload('send', data);
                                }
                            });
                            return false;
                        },
                        done: function(e, data) {
                            if (data.result.error) return;

                            var imageUrl = utils.imageUrl(data.result.public_id, self.cloudName);
                            var width = parseInt(data.result.width);
                            var height = parseInt(data.result.height);
                            var image = {
                                id : data.result.public_id,
                                url : imageUrl,
                                width : width,
                                height : height,
                                originalWidth : width,
                                originalHeight : height,
                                originalUrl : imageUrl,
                                preferredWidth : width,
                                preferredHeight : height,
                                preferredUrl : imageUrl,
                                storyUrl: imageUrl,
                                cloudName: self.cloudName,
                                isCloudinary: true
                            };

                            placeholder.before(
                            $('<div></div>')
                                    .addClass("thumb")
                                    .append(utils.scaleByHeight(image, 75))
                                    .hide()
                                    .appendTo(chapterPhotos)
                                    .fadeIn(function(){
                                    $('#photo-upload-progress').hide();
                                }));

                            self.currentImages.push(image);
                        },
                        failed: function(e, data) {
                            $('#photo-upload-progress-fill').addClass('failed');
                            $('#photo-upload-progress-text').text('Failed')
                        }});

                    $('#thumb-placeholder').attr("src", self.properties.urls.images + "/bk_img_upload_ph.png");
                    $(this).addClass('highlight');
                    if(chapter.publishedOn > 0) $('#chapter-save').hide();

                    $(this).fadeIn();
                });
                this.hideSubmits();
            },
            loadChapterTemplates: function(){
                var self = this;
                $.each(self.modelStory.get("chapters"), function(index, chapter){

                    chapter.index = index;
                    if(chapter.title === "") chapter.title = null;
                    var template = templateChapter(chapter);
                    var chapterRow = $('<div class="field-main-row clearfix"></div>').html(template).appendTo(self.body).attr("id", "chapter-row-" + index);
                    var photos = chapterRow.find('.story-input-photos');
                    var imagesFound = false;
                    var chapterImages = self.modelStory.getChapterImages(chapter);

                    $.each(chapterImages, function(index, chapterImage){
                        photos.append(utils.scaleByHeight(chapterImage.image, 50).addClass('story-summary-photo'));
                        imagesFound = true;
                    });

                    if (imagesFound === false) chapterRow.find('.story-input-photo-row').hide();
                    if (chapter.publishedOn > 0) $("#story-hide").hide();
                });
            },
            loadStoryInputTemplate: function(){
                var self = this;

                var topic = this.modelStory.get("topic");
                var story = this.modelStory.get("story");
                var partner  = this.modelStory.get("partner");

                var template = templateStoryCoverInput({ story: story, topic: topic, partner: partner, context: this.modelContext.toJSON()});

                self.cover.fadeOut(function(){
                    $(this).html(template);

                    if(story.image !== null){
                        var photo = utils.scaleByWidth(story.image, 75);
                        $('#story-input-photo').attr({
                            src: photo.attr('src'),
                            width: photo.attr('width'),
                            height: photo.attr('height')});
                        $('#story-input-imageId').val(story.image.id);
                    }
                    self.hideSubmits();
                    $(this).addClass("highlight").fadeIn();
                });
            },
            submitCover: function(){
                var self = this;
                var title = $('#story-name').val();
                if($.trim(title) === ""){
                    alert("Please include a title for your story");
                } else if(self.locked !== true){

                    var partnerId = $('#story-input-partnerId').val() ? $('#story-input-partnerId').val() : null;
                    var storyData = {
                        storyId: this.modelStory.id,
                        title: title,
                        partnerId: partnerId,
                        contentType: this.modelContext.getContentType().singular
                    };

                    var imageId = $('#story-input-imageId').val();
                    if(imageId) storyData.imageId = imageId;

                    this.modelStory.submitCover(storyData, function(model){
                        self.locked = false;
                        self.render();
                    });
               }
            },
            addChapterClick: function(){
//                this.loadChapterInputTemplate({});
                this.close();
            },
            publishChapterClick: function(){
                this.updateChapter(true);
            },
            saveChapterClick: function(){
                this.updateChapter(false);
            },
            postLink: function() {
                var self = this;

                var link = $.trim($('#link-url').val());
                if (link.length < 4) {
                    //we have only one link per chapter right now...
                    self.currentLinks = [];
                    $('#link-description').val("");
                    return;
                }
                if (self.currentLinks[0] && self.currentLinks[0].url && self.currentLinks[0].url === link) {
                    return;
                }

                self.currentLinks[0] = { url: link };


                var success = function(response) {
                    if (response.error) return;

                    self.currentLinks[0] = response;
                    var desc = $.trim($('#link-description').val());
                    if (desc) self.currentLinks[0].description = desc;
                    else $('#link-description').val(self.currentLinks[0].title)

                    var imageUrl = utils.fit(self.currentLinks[0].image, 100, 100)[0].src;
                    var placeholder = $('#link-thumbnail-placeholder').css("background-image", 'url("' + imageUrl + '")');
                };

                //kick off our processing of the link which is slow...
                utils.AjaxFactory({
                    url: self.properties.urls.site + "/story/" + self.modelStory.id + "/link",
                    type: "POST",
                    data: {
                        url: link
                    },
                    success: function(response) {
                        if (response.error === "timeout") {
                            //try once more...
                            utils.AjaxFactory({
                                url: self.properties.urls.site + "/story/" + self.modelStory.id + "/link",
                                type: "POST",
                                data: {
                                    url: link
                                },
                            success: success
                            })();
                        } else {
                            success(response);
                        }
                    }
                })();
            },
            addLinkDescription: function() {
                var self = this;
                var link = self.currentLinks[0] ? self.currentLinks[0] : {};
                link.description = $.trim($('#link-description').val());
            },
            finishStoryClick: function(){
                var self = this;
                self.unload(function(){
                    self.EvAg.trigger('router/me');
                    window.location.hash = "#!story/" + self.modelStory.id
                });
            },
            cancelChapterClick: function(){
                this.close();
            },
            hideStoryClick: function() {
                var v = confirm("Are you sure you want to hide this story?");
                if(v === true) this.modelStory.moderate();
            },
            updateChapter: function(publishOption){
                var self = this;
                var title = $.trim($('#chapter-title').val());
                var text = $.trim($('#chapter-text').val());
                var links = self.currentLinks;
                var images = self.currentImages ? self.currentImages : [];
                images = _.map(images, function(img) { return JSON.stringify(img); });
                if (text === "" && images.length <= 0 && links.length <= 0) {
                    alert("You must have either a description, an image, or a link");
                } else if (links.length > 0 && (links[0].url === "" || links[0].description === "")) {
                    alert("You must have both a link url and description");
                } else if(self.locked === false){
                    self.locked = true;
                    var options = {
                        title: title,
                        text: text,
                        imageIds: images,
                        links: self.currentLinks,
                        publish: publishOption
                    };
                    if(this.editChapterId) options.chapterId = this.editChapterId;
                    this.modelStory.saveChapter(options, function(model, response){
                        self.locked = false;
                        self.render();
                        if (options.publish) {
                            self.EvAg.trigger("exhibit/reload");
                        }
                    });
               }
            },
            hideSubmits: function(){
                this.element.find('.field-edit').hide();
                $('#story-summary-buttons').hide();
            },
            show: function(){
                this.element.fadeIn();
                $("body").addClass("noScroll");
                $("#story-name").focus();
            },
            close: function(){
                this.loaded = false;
                this.element.fadeOut().empty();
                $("body").removeClass("noScroll");
                this.EvAg.trigger('hash:reset');
            }
        });

    }
)