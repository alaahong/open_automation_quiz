
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
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

        //打开页面处理弹窗
        driver.get("https://www.ianzhang.cn/bing/");
        driver.switchTo().alert().accept();
        //选择bing iframe
        driver.switchTo().frame("bing");
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//input[@id='kw']")));

        // 在搜索框中输入姓名并搜索
        WebElement searchBox = driver.findElement(By.xpath("//input[@id='kw']"));
        searchBox.sendKeys("verna");

        WebElement searchButton = driver.findElement(By.xpath("//input[@type='submit']"));
        searchButton.click();

        // 导航到搜索结果的第二页
        navigateToSecond(driver);

        // 处理姓名的搜索结果
        processSearchResults(driver);

        // 在当前搜索结果页继续搜索 "Selenium"
        searchBox.clear();
        searchBox.sendKeys("Selenium");
        searchButton.click();

        // 导航到搜索结果的第二页
        navigateToSecond(driver);

        // 处理 "Selenium" 的搜索结果
        processSearchResults(driver);

        //关闭浏览器
        driver.quit();
    }

    public static void navigateToSecond(WebDriver driver) throws InterruptedException {
        Thread.sleep(3000);
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
        WebDriverWait wait = new WebDriverWait(driver,Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//span[@class='page-item_M4MDr pc' and text()=2]")));
        driver.findElement(By.xpath("//span[@class='page-item_M4MDr pc' and text()=2]")).click();
    }

    public static void processSearchResults(WebDriver driver) {
        System.out.println("结果列表");
        // 定义Map存储每个顶级域名出现的次数
        Map<String, Integer> domainCount = new HashMap<>();
        // 获取搜索结果的链接元素
        List<WebElement> resultLinks = driver.findElements(By.cssSelector("div h3 a"));
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
        Pattern pattern = Pattern.compile("^(?:https?://)?([^/?#]+)");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            String[] str= matcher.group(1).split("\\.");
            return "."+str[str.length-1];
        }
        return null;
    }
}