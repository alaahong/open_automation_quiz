package cn.verna.automation.steps;

import cn.verna.automation.basicmethod.BasicFunctions;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.time.Duration;

public class FirstPage {
    WebDriver driver = BasicFunctions.launchBrowser();
    @Given("open chrome browser and access to company page")
    public void open_chrome_browser_and_access_to_company_page() throws InterruptedException {
        // Write code here that turns the phrase above into concrete actions
        driver.get("https://jinshuju.net/templates/detail/Dv9JPD");
        Thread.sleep(3000);
    }

    @When("select company info")
    public void select_company_info() {
        // Write code here that turns the phrase above into concrete actions
        //找到内嵌位置的元素，打算先点击然后滚动
        try {
            driver.findElement(By.className("TemplatePreview_desktopPreview__ijZv4")).click();
            System.out.println("header find");
        }catch (Exception e){
            System.out.println("can't find header");
        }

        try {
            //尝试找到包含内嵌滚动条的元素，然后向下滚动,不起作用
            WebElement innerElement = driver.findElement(By.className("TemplatePreview_iframe__Ep1Or"));
            JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;
            jsExecutor.executeScript("arguments[0].scrollTop = arguments[0].scrollHeight", innerElement);

            //尝试直接点击单选按钮，不起作用
//            List<WebElement> elements = driver.findElements(By.className("Radio_radio__AMdMG"));
//            System.out.println(elements.size());
//            elements.get(1).click();

        }catch (Exception e){
            System.out.println("can't find radio");
        }
    }

    @And("take screenshot")
    public void take_screenshot() throws IOException {
        // Write code here that turns the phrase above into concrete actions
        BasicFunctions.takeScreen(driver,"first_page.png");
    }

    @And("click next button")
    public void click_next_button() {
        // Write code here that turns the phrase above into concrete actions
        driver.findElement(By.xpath("//span[text()='下一页']")).click();
    }

    @Then("second page display")
    public void second_page_display() {
        // Write code here that turns the phrase above into concrete actions
        try
        {
            new WebDriverWait(driver, Duration.ofSeconds(5)).until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//input[@placeholder='年-月-日' and @type='text']")));
        }
        catch (Exception e)
        {
            System.out.println("next page didn't display");
        }
    }
}
