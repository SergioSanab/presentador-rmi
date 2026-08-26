description = [[
Detects the intentionally vulnerable SQL Injection endpoint
of the SQL Injection Laboratory.

LABORATORY USE ONLY.
]]

author = "SQL Injection Laboratory"

license = "Same as Nmap--See https://nmap.org/book/man-legal.html"

categories = {"vuln", "safe"}

local http = require "http"
local shortport = require "shortport"

portrule = shortport.http

action = function(host, port)

    local path =
        "/sql-lab/index.php?q=%27"

    local response =
        http.get(host, port, path)

    if not response then
        return nil
    end

    if response.status == 200 and
       response.body and
       response.body:match("Error SQL") then

        return [[
VULNERABLE: SQL Injection detected.

The application appears to concatenate
the HTTP parameter "q" directly into
an SQL statement.
]]

    end

    return nil
end
