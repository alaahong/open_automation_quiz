
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BingSearch {
    public static void main(String[] args) throws InterruptedException {
        // 使用options的方式启动浏览器，selenium4
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");
        System.setProperty("webdriver.chrome.driver", "src/main/resources/chromedriver.exe");
        WebDriver driver = new ChromeDriver(options);

        // 打开 bing 搜索页面
        driver.get("https://cn.bing.com/");

        // 在搜索框中输入姓名并搜索
        WebElement searchBox = driver.findElement(By.id("sb_form_q"));
        searchBox.sendKeys("verna");

        WebElement searchButton = driver.findElement(By.id("search_icon"));
        searchButton.click();

        // 导航到搜索结果的第二页
        navigateToSecond(driver);

        // 处理姓名的搜索结果
        processSearchResults(driver);

        // 在当前搜索结果页继续搜索 "Selenium"
        searchBox = driver.findElement(By.id("sb_form_q"));
        searchBox.clear();
        searchBox.sendKeys("Selenium");
        searchButton = driver.findElement(By.id("sb_form_go"));
        searchButton.click();


        // 导航到搜索结果的第二页
        navigateToSecond(driver);

        // 处理 "Selenium" 的搜索结果
        processSearchResults(driver);

        //关闭浏览器
        driver.quit();
    }

    public static void navigateToSecond(WebDriver driver) throws InterruptedException {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
        Thread.sleep(3000);
        driver.findElement(By.xpath("//a[@aria-label='第 2 页']")).click();
    }

    public static void processSearchResults(WebDriver driver) {
        System.out.println("结果列表");
        // 定义Map存储每个顶级域名出现的次数
        Map<String, Integer> domainCount = new HashMap<>();
        // 获取搜索结果的链接元素
        List<WebElement> resultLinks = driver.findElements(By.cssSelector("li.b_algo h2 a"));
        for (WebElement link : resultLinks) {
            String title = link.getText();
            String url = link.getAttribute("href");
            System.out.println(title + "  --> " + url);

            // 提取顶级域名
            String domain = getDomain(url);
            domainCount.put(domain, domainCount.getOrDefault(domain, 0) + 1);
        }

        System.out.println("\n结果统计");
        for (Map.Entry<String, Integer> entry : domainCount.entrySet()) {
            System.out.println(entry.getKey() + "  --> " + entry.getValue());
        }
        System.out.println();
    }

    public static String getDomain(String url) {
        // 定义正则表达式来匹配顶级域名
        Pattern pattern = Pattern.compile("^(?:https?://)?(?:www\\.)?([^/?#]+)");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}