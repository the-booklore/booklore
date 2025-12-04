local Dispatcher = require("dispatcher")
local InfoMessage = require("ui/widget/infomessage")
local UIManager = require("ui/uimanager")
local WidgetContainer = require("ui/widget/container/widgetcontainer")
local logger = require("logger")
local _ = require("gettext")

-- Load plugin modules with unique names to avoid conflicts with other plugins
local BookLoreSettings = require("booklore_settings")
local upload = require("booklore_upload")
local const = require("booklore_const")

local BookLore = WidgetContainer:extend{
    name = "booklore",
    is_doc_only = false,
}

function BookLore:init()
    self.ui.menu:registerToMainMenu(self)

    -- Register dispatcher action for gestures
    Dispatcher:registerAction("booklore_sync", {
        category = "none",
        event = "BookLoreSync",
        title = _("BookLore sync"),
        general = true,
    })

    logger.info("BookLore plugin initialized, version", const.VERSION)
end

function BookLore:addToMainMenu(menu_items)
    menu_items.booklore = {
        text = _("BookLore"),
        sorting_hint = "tools",
        sub_item_table = {
            BookLoreSettings:new(self):getMenu(),
            {
                text = _("Sync now"),
                callback = function()
                    self:sync(false)
                end
            },
            {
                text = _("Sync on suspend"),
                checked_func = function()
                    local booklore_settings = G_reader_settings:readSetting("booklore") or {}
                    return booklore_settings.sync_on_suspend == true
                end,
                callback = function()
                    local booklore_settings = G_reader_settings:readSetting("booklore") or {}
                    booklore_settings.sync_on_suspend = not booklore_settings.sync_on_suspend
                    G_reader_settings:saveSetting("booklore", booklore_settings)
                end
            },
            {
                text = _("About"),
                keep_menu_open = true,
                callback = function()
                    UIManager:show(InfoMessage:new{
                        text = string.format("BookLore Sync v%s\n\nSyncs your reading statistics to your BookLore server.", const.VERSION),
                    })
                end
            },
        },
    }
end

function BookLore:sync(silent)
    local booklore_settings = G_reader_settings:readSetting("booklore") or {}
    local server_url = booklore_settings.server_url
    local username = booklore_settings.username
    local password_md5 = booklore_settings.password_md5

    if not server_url or server_url == "" then
        if not silent then
            UIManager:show(InfoMessage:new{
                text = _("Please configure BookLore server URL first"),
                timeout = 3,
            })
        end
        logger.warn("BookLore: Server URL not configured")
        return
    end

    if not username or username == "" or not password_md5 or password_md5 == "" then
        if not silent then
            UIManager:show(InfoMessage:new{
                text = _("Please configure KOReader credentials first"),
                timeout = 3,
            })
        end
        logger.warn("BookLore: Credentials not configured")
        return
    end

    logger.info("BookLore: Starting sync to", server_url)
    upload(server_url, username, password_md5, silent)
end

function BookLore:onSuspend()
    local booklore_settings = G_reader_settings:readSetting("booklore") or {}
    if booklore_settings.sync_on_suspend == true then
        logger.info("BookLore: Syncing on suspend")

        -- Check if we have WiFi
        local NetworkMgr = require("ui/network/manager")
        if NetworkMgr:isOnline() then
            self:sync(true)
        else
            logger.info("BookLore: Skipping sync - no network connection")
        end
    end
end

function BookLore:onBookLoreSync()
    self:sync(false)
end

return BookLore
