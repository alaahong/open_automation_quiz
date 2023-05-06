

from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.wait import WebDriverWait
from selenium.common.exceptions import NoSuchElementException
from selenium.common.exceptions import StaleElementReferenceException
from bs4 import BeautifulSoup



browser_path = r"C:\\Users\\Administrator\\AppData\\Local\\Programs\\Python\\Python36\\chromedriver"
browser = webdriver.Chrome(browser_path)
browser.get('https://www.baidu.com')
browser_input = browser.find_element_by_id('kw')
browser_input.clear()
query = "selenium"
browser_input.send_keys(query)
browser_input.send_keys(Keys.RETURN)
ignored_exceptions = (NoSuchElementException, StaleElementReferenceException,)
try:
    WebDriverWait(browser, 10, ignored_exceptions=ignored_exceptions) \
        .until(EC.title_contains(query))
except:
    continued

# 使用BeautifulSoup解析搜索结果
bsobj = BeautifulSoup(browser.page_source, features="html.parser")

# 获取搜索结果队列
search_results = bsobj.find_all('div', {'class': 'result c-container'})

# 对于每一个搜索结果
for item in search_results:
    # 获取每个搜索结果的标题的所有文本
    text = search_item.h3.a.get_text(strip=True)
    # 获取每个搜索结果的标题的标红关键字
    keywords = search_item.h3.a.find_all('em')
    # 获取每个搜索结果的摘要内容中的所有文本
    text = search_item.div.get_text(strip=True)
    # 获取每个搜索结果的摘要内容中的标红关键字
    keywords = search_item.div.find_all('em')
    print(text)
    print(keywords)

browser.close()
