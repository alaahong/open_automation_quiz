package cn.ianzhang.automation;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;


import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SeleniumChromeTest {
    public WebDriver driver;
    static String baseUrl = "https://www.ianzhang.cn/bing/";

    @Test
    public void printSearchResult() throws Exception {
        Map<String, Integer> map = new HashMap<String, Integer>();
        String firstSearch = "您的名字";
        String secondSearch = "Selenium";
        DesiredCapabilities dc = new DesiredCapabilities();
        dc.setCapability(CapabilityType.UNEXPECTED_ALERT_BEHAVIOUR, UnexpectedAlertBehaviour.IGNORE);

        driver = new ChromeDriver(dc);
        driver.get(baseUrl);
        //Accept the Alert window
        try {
            Alert alert = driver.switchTo().alert();
            String alertText = alert.getText();
            System.out.println("Alert data: " + alertText);
            alert.accept();
            driver.switchTo().frame("bing");
        } catch (UnhandledAlertException f) {
            return;
        }
        //1st Search
        System.out.println("==============1st Search===============");
        new WebDriverWait(driver, Duration.ofMillis(10000)).until(ExpectedConditions.visibilityOfElementLocated(By.id("sb_form_q")));
        WebElement searchBox = driver.findElement(By.id("sb_form_q"));
        WebElement searchButton = driver.findElement(By.id("search_icon"));
        searchBox.sendKeys(firstSearch);
        searchButton.click();
        //wait the page loading and set window size
        driver.manage().timeouts().implicitlyWait(Duration.ofMillis(10000));
        driver.manage().window().setSize(new Dimension(2560,1440));
       //Dismiss the cover and scroll to the bottom of page, them switch to 2nd page
        JavascriptExecutor je = (JavascriptExecutor) driver;
        new WebDriverWait(driver, Duration.ofMillis(10000)).until(ExpectedConditions.visibilityOfElementLocated(By.id("bnp_hfly_cta2")));
        WebElement  mayBeLaterBtn= driver.findElement(By.id("bnp_hfly_cta2"));
        new WebDriverWait(driver, Duration.ofMillis(10000)).until(ExpectedConditions.elementToBeClickable(By.id("bnp_hfly_cta2")));
        je.executeScript("arguments[0].click();", mayBeLaterBtn);
        je.executeScript("window.scrollTo(0, document.body.scrollHeight)");
        driver.manage().timeouts().implicitlyWait(Duration.ofMillis(500));
        WebElement page = driver.findElement(By.cssSelector("#b_results > li.b_pag > nav > ul > li:nth-child(2) > a"));
        page.click();
        //Print the results' title and URL
        new WebDriverWait(driver, Duration.ofMillis(10000)).until(ExpectedConditions.visibilityOfElementLocated(By.id("sb_form_q")));
        List<WebElement> url_result= driver.findElements(By.xpath("//*[@id=\"b_results\"]/li/h2/a"));
        System.out.println("Result Amount for " + firstSearch +":" + url_result.size());
        System.out.println("----结果列表----");
        for (WebElement result:url_result) {
            System.out.println(result.getText() + "==>" +result.getAttribute("href"));
        }
        System.out.println("----结果统计----");
        for (WebElement result:url_result) {
            URL url = new URL(result.getAttribute("href"));
            if (map.containsKey(url.getAuthority())) {
                int count = map.get(url.getAuthority());
                map.put(url.getAuthority(), ++count);
            } else {
                map.put(url.getAuthority(), 1);
            }
        }
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            System.out.println(entry.getKey() + ":" + entry.getValue());
        }
        //re-initialize hashmap for 2nd search
        map = new HashMap<>();

        //Start to 2nd search
        System.out.println("================2nd Search=================");
        WebElement searchInput = driver.findElement(By.id("sb_form_q"));
        searchInput.clear();
        searchInput.sendKeys(secondSearch);
        WebElement searchBtn = driver.findElement(By.id("sb_form_go"));
        searchBtn.click();
        //scroll to the bottom of page, them switch to 2nd page
        new WebDriverWait(driver, Duration.ofMillis(10000)).until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#b_results > li.b_pag > nav > ul > li:nth-child(2) > a")));
        je.executeScript("window.scrollTo(0, document.body.scrollHeight)");
        WebElement page2 = driver.findElement(By.cssSelector("#b_results > li.b_pag > nav > ul > li:nth-child(2) > a"));
        new WebDriverWait(driver, Duration.ofMillis(10000)).until(ExpectedConditions.elementToBeClickable(By.cssSelector("#b_results > li.b_pag > nav > ul > li:nth-child(2) > a")));
        page2.click();
        //Print the results' title and URL
        List<WebElement> url_secresult= driver.findElements(By.xpath("//*[@id=\"b_results\"]/li/h2/a"));
        System.out.println("Result Amount for " + secondSearch +":" + url_result.size());
        System.out.println("----结果列表----");
        for (WebElement result:url_secresult) {
            System.out.println(result.getText() + "==>" +result.getAttribute("href"));
        }
        System.out.println("----结果统计----");
        for (WebElement result:url_secresult) {
            URL url = new URL(result.getAttribute("href"));
            if (map.containsKey(url.getAuthority())) {
                int count = map.get(url.getAuthority());
                map.put(url.getAuthority(), ++count);
            } else {
                map.put(url.getAuthority(), 1);
            }
        }
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            System.out.println(entry.getKey() + ":" + entry.getValue());
        }

        driver.quit();
    }
}
