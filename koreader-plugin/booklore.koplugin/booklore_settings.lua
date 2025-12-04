local InfoMessage = require("ui/widget/infomessage")
local InputDialog = require("ui/widget/inputdialog")
local UIManager = require("ui/uimanager")
local _ = require("gettext")
local sha2 = require("ffi/sha2")

local BookLoreSettings = {}

function BookLoreSettings:new(plugin)
    local o = {
        plugin = plugin
    }
    setmetatable(o, self)
    self.__index = self
    return o
end

function BookLoreSettings:getMenu()
    return {
        text = _("Configure"),
        keep_menu_open = true,
        callback = function()
            self:showConfigDialog()
        end
    }
end

function BookLoreSettings:showConfigDialog()
    local booklore_settings = G_reader_settings:readSetting("booklore") or {}
    local server_url = booklore_settings.server_url or ""
    local username = booklore_settings.username or ""

    local dialog
    dialog = InputDialog:new{
        title = _("BookLore Configuration"),
        input = server_url,
        input_hint = "https://your-booklore-server.com",
        description = _("Enter your BookLore server URL"),
        buttons = {
            {
                {
                    text = _("Cancel"),
                    callback = function()
                        UIManager:close(dialog)
                    end,
                },
                {
                    text = _("Save"),
                    is_enter_default = true,
                    callback = function()
                        local url = dialog:getInputText()
                        -- Ensure URL has proper protocol format
                        if url and url ~= "" then
                            -- Fix common URL formatting issues
                            url = url:gsub("^http:/([^/])", "http://%1")  -- Fix http:/ to http://
                            url = url:gsub("^https:/([^/])", "https://%1")  -- Fix https:/ to https://
                        end
                        local booklore_settings = G_reader_settings:readSetting("booklore") or {}
                        booklore_settings.server_url = url
                        G_reader_settings:saveSetting("booklore", booklore_settings)
                        UIManager:close(dialog)
                        self:showUsernameDialog()
                    end,
                },
            }
        },
    }
    UIManager:show(dialog)
    dialog:onShowKeyboard()
end

function BookLoreSettings:showUsernameDialog()
    local booklore_settings = G_reader_settings:readSetting("booklore") or {}
    local username = booklore_settings.username or ""

    local dialog
    dialog = InputDialog:new{
        title = _("KOReader Username"),
        input = username,
        input_hint = "koreader_username",
        description = _("Enter your KOReader sync username"),
        buttons = {
            {
                {
                    text = _("Cancel"),
                    callback = function()
                        UIManager:close(dialog)
                    end,
                },
                {
                    text = _("Next"),
                    is_enter_default = true,
                    callback = function()
                        local user = dialog:getInputText()
                        local booklore_settings = G_reader_settings:readSetting("booklore") or {}
                        booklore_settings.username = user
                        G_reader_settings:saveSetting("booklore", booklore_settings)
                        UIManager:close(dialog)
                        self:showPasswordDialog()
                    end,
                },
            }
        },
    }
    UIManager:show(dialog)
    dialog:onShowKeyboard()
end

function BookLoreSettings:showPasswordDialog()
    local dialog
    dialog = InputDialog:new{
        title = _("KOReader Password"),
        input = "",
        input_hint = "password",
        text_type = "password",
        description = _("Enter your KOReader sync password"),
        buttons = {
            {
                {
                    text = _("Cancel"),
                    callback = function()
                        UIManager:close(dialog)
                    end,
                },
                {
                    text = _("Save"),
                    is_enter_default = true,
                    callback = function()
                        local pass = dialog:getInputText()
                        local password_md5 = sha2.md5(pass)
                        local booklore_settings = G_reader_settings:readSetting("booklore") or {}
                        booklore_settings.password_md5 = password_md5
                        G_reader_settings:saveSetting("booklore", booklore_settings)
                        G_reader_settings:flush()  -- Force save to disk
                        UIManager:close(dialog)
                        UIManager:show(InfoMessage:new{
                            text = _("Settings saved successfully!"),
                            timeout = 2,
                        })
                    end,
                },
            }
        },
    }
    UIManager:show(dialog)
    dialog:onShowKeyboard()
end

return BookLoreSettings
