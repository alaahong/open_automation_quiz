
package cn.ianzhang.automation;

import io.cucumber.java.zh_cn.假如;
import io.cucumber.java.zh_cn.当;
import io.cucumber.java.zh_cn.那么;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;

import java.io.File;
import java.io.IOException;


public class SubmitForm {
    public WebDriver driver;
    static String baseUrl = "https://templates.jinshuju.net/detail/Dv9JPD";
    @假如(": 打开网页")
    public void openWebsite() {
        driver = new ChromeDriver();
        driver.get(baseUrl);
    }

    @当(": 用户选择单位情况然后点击下一页按钮")
    public void selectAndClick() throws InterruptedException {
        WebElement iframe =driver.findElement(By.tagName("iframe"));
        driver.switchTo().frame(iframe);
        Thread.sleep(2000);
        WebElement secChoice = driver.findElement(By.xpath("/html/body/div[1]/div/div/form/div[3]/div/div[2]/div/div/div[2]/div[1]/div/span/div/div[2]"));
        secChoice.click();
        File srcFile = ((TakesScreenshot)driver).getScreenshotAs(OutputType.FILE);
        try {
            FileUtils.copyFile(srcFile, new File("\\Users\\shuaiwang\\Downloads\\Screenshots\\第一页.jpg"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        WebElement nextBtn = driver.findElement(By.xpath("//*[@id=\"root\"]/div/form/div[5]/button"));
        nextBtn.click();
        Thread.sleep(4000);
    }

    @当(": 用户填写信息然后点击下一页按钮")
    public void fillOutAndClick() throws InterruptedException {
        WebElement applicationDate = driver.findElement(By.xpath("/html/body/div[1]/div/div/form/div[3]/div/div[4]/div/div/div[2]/div"));
        applicationDate.sendKeys("2022-01-01");
        Thread.sleep(2000);
        WebElement applicant = driver.findElement(By.xpath("/html/body/div[1]/div/div/form/div[3]/div/div[6]/div/div/div[2]/div[1]/div"));
        applicant.sendKeys("自动化");
        Thread.sleep(2000);
        WebElement phoneNumber = driver.findElement(By.xpath("/html/body/div[1]/div/div/form/div[3]/div/div[8]/div/div/div[2]/div[1]/div"));
        phoneNumber.sendKeys("13888888888");
        Thread.sleep(2000);
        File srcFile1 = ((TakesScreenshot)driver).getScreenshotAs(OutputType.FILE);
        try {
            FileUtils.copyFile(srcFile1, new File("\\Users\\shuaiwang\\Downloads\\Screenshots\\第二页.jpg"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        WebElement nextBtn1 = driver.findElement(By.xpath("//*[@id=\"root\"]/div/form/div[5]/button[2]"));
        nextBtn1.click();
        Thread.sleep(2000);
    }

    @当(": 用户填写报备表然后点击提交")
    public void fillOutAndSubmit() throws InterruptedException {
        WebElement appliedCompany = driver.findElement(By.xpath("/html/body/div[1]/div/div/form/div[3]/div[1]/div[4]/div/div/div[2]/div[1]"));
        appliedCompany.sendKeys("测试公司");
        Thread.sleep(2000);
        WebElement employeeAmount = driver.findElement(By.xpath("/html/body/div[1]/div/div/form/div[3]/div[1]/div[6]/div/div/div[2]/div[1]"));
        employeeAmount.sendKeys("99");
        Thread.sleep(2000);
        WebElement date = driver.findElement(By.xpath("/html/body/div[1]/div/div/form/div[3]/div[1]/div[8]/div/div/div[2]/div[1]"));
        date.sendKeys("2022-06-21");
        Thread.sleep(2000);
        WebElement ccemployeeAmount = driver.findElement(By.xpath("/html/body/div[1]/div/div/div/form/div[3]/div[1]/div[10]/div/div/div[2]/div[1]"));
        ccemployeeAmount.sendKeys("0");
        Thread.sleep(2000);
        WebElement principle = driver.findElement(By.xpath("/html/body/div[1]/div/div/form/div[3]/div[1]/div[12]/div/div/div[2]/div[1]"));
        principle.sendKeys("王帅");
        Thread.sleep(2000);
        WebElement phoneNumber1 = driver.findElement(By.xpath("/html/body/div[1]/div/div/form/div[3]/div[1]/div[14]/div/div/div[2]/div[1]"));
        phoneNumber1.sendKeys("13888888888");
        Thread.sleep(2000);
        WebElement planField = driver.findElement(By.xpath("/html/body/div[1]/div/div/form/div[3]/div[1]/div[16]/div/div/div[2]/div[1]/div/span/textarea"));
        planField.sendKeys("测试内容");
        Thread.sleep(2000);
        File srcFile2 = ((TakesScreenshot)driver).getScreenshotAs(OutputType.FILE);
        try {
            FileUtils.copyFile(srcFile2, new File("\\Users\\shuaiwang\\Downloads\\Screenshots\\第三页.jpg"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        WebElement submitBtn = driver.findElement(By.xpath("//*[@id=\"root\"]/div/form/div[5]/div/button[2]"));
        submitBtn.click();
        Thread.sleep(2000);
    }

    @那么(": 成功提交申请表")
    public void successToSubmit() {
        WebElement text = driver.findElement(By.xpath("/html/body/div/div[2]/div[2]/div/div[1]"));
        Assert.assertEquals("提交成功", text.getText());
        File srcFile3 = ((TakesScreenshot)driver).getScreenshotAs(OutputType.FILE);
        try {
            FileUtils.copyFile(srcFile3, new File("\\Users\\shuaiwang\\Downloads\\Screenshots\\提交成功.jpg"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}