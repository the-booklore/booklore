local JSON = require("json")
local http = require("socket.http")
local ltn12 = require("ltn12")
local logger = require("logger")
local UIManager = require("ui/uimanager")
local InfoMessage = require("ui/widget/infomessage")
local DataStorage = require("datastorage")

-- Load plugin modules with unique names to avoid conflicts with other plugins
local const = require("booklore_const")

local function upload(server_url, username, password_md5, silent)
    if not server_url then
        if not silent then
            UIManager:show(InfoMessage:new{
                text = "BookLore server URL not configured",
            })
        end
        return
    end

    -- Fix common URL formatting issues
    server_url = server_url:gsub("^http:/([^/])", "http://%1")
    server_url = server_url:gsub("^https:/([^/])", "https://%1")

    -- Get path to statistics database
    local db_path = DataStorage:getSettingsDir() .. "/statistics.sqlite3"
    local file = io.open(db_path, "rb")
    if not file then
        logger.err("BookLore: Failed to open statistics database at " .. db_path)
        if not silent then
            UIManager:show(InfoMessage:new{
                text = "Failed to open statistics database",
                timeout = 3,
            })
        end
        return
    end

    -- Read the entire file into memory
    local file_content = file:read("*all")
    file:close()

    if not file_content or #file_content == 0 then
        logger.err("BookLore: Statistics database is empty")
        if not silent then
            UIManager:show(InfoMessage:new{
                text = "Statistics database is empty",
                timeout = 3,
            })
        end
        return
    end

    -- Create multipart/form-data boundary
    local boundary = "----BookLoreBoundary" .. os.time()

    -- Build multipart body
    local body_parts = {}
    table.insert(body_parts, "--" .. boundary .. "\r\n")
    table.insert(body_parts, 'Content-Disposition: form-data; name="file"; filename="statistics.sqlite3"\r\n')
    table.insert(body_parts, "Content-Type: application/x-sqlite3\r\n")
    table.insert(body_parts, "\r\n")
    table.insert(body_parts, file_content)
    table.insert(body_parts, "\r\n--" .. boundary .. "--\r\n")

    local payload = table.concat(body_parts)

    -- Prepare the request
    local response_body = {}
    local url = server_url .. "/api/koreader/statistics/upload"

    logger.info("BookLore: Uploading statistics database to " .. url)
    logger.info("BookLore: File size: " .. #file_content .. " bytes")

    -- Make the HTTP request with multipart/form-data
    local res, code, response_headers, status = http.request{
        url = url,
        method = "POST",
        headers = {
            ["Content-Type"] = "multipart/form-data; boundary=" .. boundary,
            ["Content-Length"] = tostring(#payload),
            ["x-auth-user"] = username,
            ["x-auth-key"] = password_md5  -- Already MD5 hashed
        },
        source = ltn12.source.string(payload),
        sink = ltn12.sink.table(response_body)
    }

    if code == 200 or code == 202 then
        logger.info("BookLore: Successfully uploaded statistics (code: " .. tostring(code) .. ")")
        if not silent then
            local response = JSON.decode(table.concat(response_body))
            local message

            if code == 202 then
                -- Async processing - immediate response
                message = "Statistics uploaded!\nProcessing in background..."
            else
                -- Synchronous processing - full results
                message = string.format(
                    "Sync successful!\nImported: %d\nSkipped: %d\nDuplicates: %d",
                    response.imported or 0,
                    response.skipped or 0,
                    response.duplicates or 0
                )
            end

            UIManager:show(InfoMessage:new{
                text = message,
                timeout = 3,
            })
        end
    else
        logger.err("BookLore: Upload failed with code " .. tostring(code) .. " status: " .. tostring(status))
        if not silent then
            UIManager:show(InfoMessage:new{
                text = "Sync failed: " .. (status or tostring(code) or "Unknown error"),
                timeout = 3,
            })
        end
    end
end

return upload
