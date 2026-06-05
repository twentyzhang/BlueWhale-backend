package com.twentyzhang.bluewhale.util;

import jakarta.servlet.http.HttpServletResponse;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 设置 xlsx 下载所需的 HTTP 响应头。
 * 必须在向响应体写入任何字节之前调用。
 */
public final class ExcelDownloadUtil {

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private ExcelDownloadUtil() {}

    public static void prepareResponse(HttpServletResponse response, String filename) {
        response.setContentType(XLSX_CONTENT_TYPE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // RFC 5987：filename* 兼容含非 ASCII 字符的文件名
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);
    }
}
