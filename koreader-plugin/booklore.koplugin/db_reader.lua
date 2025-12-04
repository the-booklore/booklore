local logger = require("logger")
local DataStorage = require("datastorage")
local SQ3 = require("lua-ljsqlite3/init")

local BookLoreDbReader = {}

function BookLoreDbReader:new()
    local o = {}
    setmetatable(o, self)
    self.__index = self
    return o
end

function BookLoreDbReader:openDatabase()
    local db_path = DataStorage:getSettingsDir() .. "/statistics.sqlite3"
    local db = SQ3.open(db_path)

    if not db then
        logger.err("BookLore: Failed to open statistics database at", db_path)
        return nil
    end

    return db
end

function BookLoreDbReader:bookData()
    local db = self:openDatabase()
    if not db then
        return {}
    end

    local books = {}
    -- Use explicit column order to make access pattern clear
    local sql = "SELECT id, title, authors, notes, last_open, highlights, pages, series, language, md5, total_read_time, total_read_pages FROM book"
    local result, rows = db:exec(sql)

    if result and rows > 0 then
        for i = 1, rows do
            local book = {
                id = tonumber(result[1][i]),              -- column 1: id
                title = result[2][i],                     -- column 2: title
                authors = result[3][i],                   -- column 3: authors
                notes = tonumber(result[4][i]),           -- column 4: notes
                last_open = tonumber(result[5][i]),       -- column 5: last_open
                highlights = tonumber(result[6][i]),      -- column 6: highlights
                pages = tonumber(result[7][i]),           -- column 7: pages
                series = result[8][i],                    -- column 8: series
                language = result[9][i],                  -- column 9: language
                md5 = result[10][i],                      -- column 10: md5
                total_read_time = tonumber(result[11][i]), -- column 11: total_read_time
                total_read_pages = tonumber(result[12][i]) -- column 12: total_read_pages
            }
            logger.dbg("BookLore: Book - title='" .. tostring(book.title) .. "' md5='" .. tostring(book.md5) .. "'")
            table.insert(books, book)
        end
    end

    db:close()
    logger.dbg("BookLore: Loaded", #books, "books from database")
    return books
end

function BookLoreDbReader:progressData()
    local db = self:openDatabase()
    if not db then
        return {}
    end

    -- First, get book MD5 mappings
    local md5_by_id = {}
    local sql_books = "SELECT id, md5 FROM book"
    local book_result, book_rows = db:exec(sql_books)

    if book_result and book_rows > 0 then
        for i = 1, book_rows do
            -- Column-first access: book_result[1] = all ids, book_result[2] = all md5s
            md5_by_id[tonumber(book_result[1][i])] = book_result[2][i]
        end
    end

    -- Now get page statistics
    local stats = {}
    local sql = "SELECT id_book, page, start_time, duration, total_pages FROM page_stat_data"
    local result, rows = db:exec(sql)

    if result and rows > 0 then
        for i = 1, rows do
            -- Column-first access pattern
            local book_id = tonumber(result[1][i])      -- column 1: id_book
            local book_md5 = md5_by_id[book_id]

            if book_md5 then
                table.insert(stats, {
                    book_md5 = book_md5,
                    page = tonumber(result[2][i]),          -- column 2: page
                    start_time = tonumber(result[3][i]),    -- column 3: start_time
                    duration = tonumber(result[4][i]),      -- column 4: duration
                    total_pages = tonumber(result[5][i]),   -- column 5: total_pages
                    device_id = G_reader_settings:readSetting("device_id") or "unknown"
                })
            end
        end
    end

    db:close()
    logger.dbg("BookLore: Loaded", #stats, "reading sessions from database")
    return stats
end

return BookLoreDbReader
