require(
    [
        'requireLib',
        'jquery',
        'backbone',
        'underscore',
        'isotope',
        'components/errorLog',
        'components/infiniteScroll',
        'components/exhibit',
        'views/item/item',
        'components/input',
        'components/widget/messageHandler',
        'components/widgetCloser',
        'components/title',
        'components/login',
        'components/user',
        'routers/widget',
        'models/user',
        'models/context',
        'models/partner',
        'easyXDM',
        'isotopeConfig'
    ],
    function(requireLib, $, Backbone, _, isotope, ErrorLog, InfiniteScroll, Exhibit, Item, Input, MessageHandler, WidgetCloser, Title, Login, User, Router, ModelUser, ModelContext, ModelPartner, easyXDM){

        $(document).ready(function(){
            this.EventAggregator = _.extend({}, Backbone.Events);
            this.urls = Echoed.urls;

            //Initialize Models
            this.modelUser = new ModelUser(Echoed.echoedUser, {urls: this.urls });
            this.modelContext = new ModelContext({}, {urls : this.urls });
            this.modelPartner = new ModelPartner({}, {urls : this.urls });

            this.properties = {
                urls: this.urls,
                echoedUser: Echoed.echoedUser,
                partnerId: Echoed.partnerId,
                isWidget: true,
                isOverlay: true
            };




            //Options
            this.options = function(el){
                var opt = {
                    properties: this.properties,
                    modelUser: this.modelUser,
                    modelContext: this.modelContext,
                    modelPartner: this.modelPartner,
                    EvAg: this.EventAggregator
                };
                if(el) opt.el = el;
                return opt;
            };

            this.errorLog = new ErrorLog(this.options());
            this.exhibit = new Exhibit(this.options('#exhibit'));
            this.infiniteScroll = new InfiniteScroll(this.options('#infiniteScroll'));
            this.input = new Input(this.options('#field-container'));
            this.item = new Item(this.options('#item-container'));
            this.closer = new WidgetCloser(this.options('#close'));
            this.titleNav = new Title(this.options('#title-container'));
            this.login = new Login(this.options("#login-container"));
            this.router = new Router(this.options());
            this.user = new User(this.options('#user'));

            var iFrameNode = document.createElement('iframe');

            iFrameNode.height = '0px';
            iFrameNode.width = '0px';
            iFrameNode.style.border = "none";
            iFrameNode.id = "echoed-iframe";
            iFrameNode.src = Echoed.https.site + "/echo/iframe";
            document.getElementsByTagName('body')[0].appendChild(iFrameNode);

            this.messageHandler = new MessageHandler(this.options('#echoed-iframe'));

            Backbone.history.start();
        });
    }
);

