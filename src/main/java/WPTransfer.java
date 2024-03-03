
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author Lin Yizhou
 * @date 2024/3/2 21:13
 */
public class WPTransfer {
    private static Map<String, String> requestHeader = new HashMap<>();
    private static Map<String, String> cookieMap = new HashMap<>();
    private static String bdstoken;
    private static String surl;
    private static String shareId;
    private static String shareUk;

    //自定义内容
    private static String wpUrl = "https://pan.baidu.com/s/11ZgtL187LI2_BjZLLf236B";
    private static String wpPwd = "1234";
    private static String cookie = "";

    static {
        requestHeader.put("Host", "pan.baidu.com");
        requestHeader.put("Connection", "keep-alive");
        requestHeader.put("Upgrade-Insecure-Requests", "1");
        requestHeader.put("User_Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36");
        requestHeader.put("Sec-Fetch-Dest", "document");
        requestHeader.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9");
        requestHeader.put("Sec-Fetch-Site", "same-site");
        requestHeader.put("Sec-Fetch-Mode", "navigate");
        requestHeader.put("Referer", "https://pan.baidu.com");
        requestHeader.put("Accept-Encoding", "gzip, deflate, br");
        requestHeader.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8,en-US;q=0.7,en-GB;q=0.6,ru;q=0.5");
    }

    public static String creatDir(String name) {
        return HttpRequest.post("https://pan.baidu.com/api/create?a=commit&bdstoken=" + bdstoken)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .form("path", name)
                .form("isdir", "1")
                .form("block_list", "[]")
                .cookie(cookie)
                .execute().body();
    }

    public static String getWpDir() throws IOException {
        return Jsoup.connect("https://pan.baidu.com/api/list?order=time&desc=1&showempty=0&web=1&page=1&num=1000&dir=%2F&bdstoken=" + bdstoken)
                .timeout(20 * 1000)
                .cookies(cookieMap)
                .ignoreContentType(true)
                .execute().body();
    }

    public static String transfer(String shareid, String shareUk, Long fsId, String serverFilename) {
        String url = "https://pan.baidu.com/share/transfer?shareid=" + shareid + "&from=" + shareUk + "&bdstoken=" + bdstoken + "&channel=chunlei&web=1&clienttype=0&app_id=250528&ondup=newcopy&async=1";
        return HttpRequest.post(url)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("fsidlist=%5B" + fsId + "%5D&path=" + URLEncoder.encode(serverFilename))
                .cookie(cookie)
                .header("Referer", wpUrl)
                .execute().body();
    }

    public static String getSonDir(String shareId, String shareUk, String path) throws IOException {
        return Jsoup.connect("https://pan.baidu.com/share/list")
                .data("uk", shareUk)
                .data("shareid", shareId)
                .data("order", "other")
                .data("desc", "1")
                .data("showempty", "0")
                .data("web", "1")
                .data("page", "1")
                .data("num", "100")
                .data("bdstoken", bdstoken)
                .data("channel", "chunlei")
                .data("app_id", "250528")
                .data("clienttype", "0")
                .data("dir", path)
                .header("Referer", wpUrl)
                .headers(requestHeader)
                .cookies(cookieMap).ignoreContentType(true).execute().body().trim();
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        surl = wpUrl.substring(25);
        cookieMap = stringToMap(cookie);
        bdstoken = JSONUtil.parseObj(Jsoup.connect("https://pan.baidu.com/api/gettemplatevariable?clienttype=0&app_id=250528&web=1&fields=[%22bdstoken%22,%22token%22,%22uk%22,%22isdocuser%22,%22servertime%22]")
                        .timeout(20 * 1000)
                        .cookies(cookieMap)
                        .ignoreContentType(true)
                        .execute().body().trim())
                .getJSONObject("result").getStr("bdstoken");
        cookieMap.put("BDCLND", JSONUtil.parseObj(HttpRequest.post("https://pan.baidu.com/share/verify?surl=" + surl + "&channel=chunlei&web=1&app_id=250528&bdstoken=&logid=&clienttype=0")
                .body("pwd=" + wpPwd + "&vcode=&vcode_str=")
                .header("Referer", "https://pan.baidu.com/share/init?surl=" + surl)
                .execute().body()).getStr("randsk"));
        cookie += "; BDCLND=" + cookieMap.get("BDCLND");
        String body = Jsoup.connect(wpUrl).headers(requestHeader).cookies(cookieMap).execute().body();
        int end = body.indexOf("window.BadSDK && window.BadSDK.updateConfig");
        int start = body.indexOf("locals.mset(");
        String trim = body.substring(start + 12, end).trim();
        JSONObject jsonObject = JSONUtil.parseObj(trim.substring(0, trim.length() - 2));
        shareId = jsonObject.getStr("shareid");
        shareUk = jsonObject.getStr("share_uk");
        Deque<FileInfo> fileDeque = new LinkedList<>();
        jsonObject.getJSONArray("file_list").forEach(o -> fileDeque.push(JSONUtil.toBean(JSONUtil.toJsonStr(o), FileInfo.class)));
        while (fileDeque.size() > 0) {
            FileInfo fileInfo = fileDeque.poll();
            String transfer = transfer(shareId, shareUk, fileInfo.getFsId(), fileInfo.getPath().replace(fileInfo.getServerFilename(), ""));
            Thread.sleep(5000);
            System.out.println(transfer);
            JSONObject parseObj = JSONUtil.parseObj(transfer);
            String errno = parseObj.getStr("errno");
            if ("0".equals(errno)) continue;
            if (transfer.contains("转存文件数超限")) {
                System.out.println(creatDir(fileInfo.getPath()));
                String sonDir = getSonDir(shareId, shareUk, fileInfo.getIsdir() != 1 ? fileInfo.getPath() + fileInfo.getServerFilename() : fileInfo.getPath());
                JSONUtil.parseObj(sonDir).getJSONArray("list").forEach(o -> fileDeque.push(JSONUtil.toBean(JSONUtil.toJsonStr(o), FileInfo.class)));
            } else {
                System.out.println("转存失败");
                return;
            }
        }
        System.out.println("转存结束！！！");
    }


    public static Map<String, String> stringToMap(String input) {
        return Arrays.stream(input.split(";\\s*"))
                .map(s -> s.split("=", 2))
                .filter(pair -> pair.length == 2)
                .collect(Collectors.toMap(pair -> pair[0], pair -> pair[1]));
    }
}

@NoArgsConstructor
@Data
@Accessors(chain = true)
class FileInfo {
    @JsonProperty("category")
    private Integer category;
    @JsonProperty("fs_id")
    private Long fsId;
    @JsonProperty("isdir")
    private Integer isdir;
    @JsonProperty("path")
    private String path;
    @JsonProperty("server_filename")
    private String serverFilename;
    @JsonProperty("size")
    private Integer size;
    @JsonProperty("md5")
    private String md5;
}
