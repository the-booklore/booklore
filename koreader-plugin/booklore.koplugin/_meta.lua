local _ = require("gettext")
return {
    name = "booklore",
    fullname = _("BookLore Sync"),
    description = _([[Syncs your reading statistics to your BookLore server.]]),
    -- Make the plugin visible in Plugin Management
    sorting_hint = "BookLore",
}
