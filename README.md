# Open Automation Quiz

![CI Build](https://github.com/alaahong/open_automation_quiz/workflows/CI/badge.svg)
![Nightly Build](https://github.com/alaahong/open_automation_quiz/actions/workflows/nightly.yml/badge.svg)
[![codecov](https://codecov.io/github/alaahong/open_automation_quiz/graph/badge.svg?token=GEBB7Z1AZK)](https://codecov.io/github/alaahong/open_automation_quiz)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=alaahong_open_automation_quiz&metric=coverage)](https://sonarcloud.io/summary/new_code?id=alaahong_open_automation_quiz)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=alaahong_open_automation_quiz&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=alaahong_open_automation_quiz)


---

## 0. 任务要求

> **特别注意**：请于完成后，将项目源码更新至候选者您本人的Github，在您项目的**Settings**页面，例如本项目为此[链接](https://github.com/alaahong/open_automation_quiz/settings/access) ，将您的项目设置为**Private Repository**以免借阅, 并于**Manage access**邀请**alaahong**作为协作者(collaborator)后，发起[issue](https://github.com/alaahong/open_automation_quiz/issues)声明您的项目链接，并最终反馈给HR/Vendor/猎头等渠道

### 基本要求

* 请**Fork**本项目到您自己的Github账号下 (本项目已关联Github Actions, 会自动编译检测Fork项目提交质量)
* 以下内容均基于**Java**进行考察，并同时涉及到了Git, Maven, Selenium, Cucumber 和 Appium等技术
* 第一题Selenium和第二题Cucumber必做，第三题Appium视为加分项可以选做
* 网页端内容需对[Chrome](https://www.google.cn/intl/zh-CN/chrome/)浏览器进行实现,有余力的同学可以考虑实现基于[Microsoft Edge (IE Mode)](https://www.microsoft.com/zh-cn/edge/business/ie-mode)的方案
* 手机端内容可以基于Android或iOS平台二选一
* 若担心环境问题，可以将运行结果的截图添加至项目资源目录内，并声明截图路径
* 允许锦上添花，额外增加体现个人情况的功能，但是无视题目要求自行发挥的，直接判定失败

### 加分项

希望，我们可以遇到这样的您

* 认真理解题目的要求，若有不明确的地方，可以直接提[issue](https://github.com/alaahong/open_automation_quiz/issues)沟通 或 通过HR/Vendor/猎头等反馈
* 思路清晰，代码规范，尽量完成了更多的任务，针对**项目结构**和**代码质量**进行了完善
* 尽量提交可以直接运行的项目，至少也应该是可以通过**Maven**构建的 [![Build Status](https://github.com/alaahong/open_automation_quiz/workflows/CI/badge.svg)](https://github.com/alaahong/open_automation_quiz)
* 提交一个规范的Java项目，符合标准的项目结构，根据需求引入必要的依赖并解决冲突，创建必要的文件和配置
* 项目不依赖于特定的IDE，可以通过命令行或者接口的形式被调用，以便于测试平台或框架级别的引用

> 以下任务内容均来源于基本的日常需求，请您反馈项目前认真思考，是否适应并胜任，比这些任务更加复杂的日常工作

### 度量标准

这套开放测试题目已运行数年，针对有经验的候选人，可以有效甄别候选人是否熟练掌握基于Java的UI自动化测试能力、编码习惯以及分析和解决问题的方式方法。
此套题目同样适用于没有过多经验的职场新人，根据实际考察效果观察，基础比较好的同学可以在一个周末内通过自学完成题目要求。

此外，针对完美解决此处三道题的同学，可以考虑另外一套 [进阶版的试题](https://www.ianzhang.cn/bing/skill_level/intermediate/) ，更加考察大家的观察和学习能力。
根据实际测试，日常维持一定比率代码编写和调试工作，并且有一定解决问题经验的测试开发工程师，可以在一个小时之内完成题目要求。

### 校验方式

项目根目录执行以下命令后，查看即时生成的测试结果及报告

> mvn clean test

---

## 1. 搜索统计(Selenium)

请使用[Selenium](https://github.com/SeleniumHQ/selenium)打开[测试网站](https://www.ianzhang.cn/bing/)，在搜索栏内输入关键词 "**您的姓名**" 并执行搜索操作，请基于搜索结果的**第二页**内容，请打印每个结果的标题以及链接，同时统计并打印每个**二级域名**出现的次数。
完成后请在上一步的搜索结果页，继续基于关键词 "**Selenium**" 执行同样的打印操作。

例：若得到以下的搜索结果
[Bing Translator](https://www.bing.com/Translator) 、
[Bing](https://cn.bing.com/?setmkt=de-de&setlang=de-de) 、
[bing（搜索引擎）_百度百科](https://baike.baidu.com/item/bing/5994319)

则应输出

```
结果列表  
Bing Translator  --> www.bing.com/Translator   
Bing  --> https://cn.bing.com/?setmkt=de-de&setlang=de-de  
bing（搜索引擎）_百度百科  --> https://baike.baidu.com/item/bing/5994319   

结果统计 
bing.com  --> 2
baidu.com  --> 1  
```

---

## 2. 企业复工申请资料提交表(Cucumber)

请基于[Cucumber](https://cucumber.io/)，以[BDD](https://cucumber.io/docs/bdd/)的形式，自行实现所需的[Feature](https://cucumber.io/docs/gherkin/reference/#feature)和[Gherkin](https://cucumber.io/docs/gherkin/)，用于以下操作
打开网页 [企业复工申请资料提交表](https://templates.jinshuju.net/detail/Dv9JPD)
请在第一页填写以下内容
“**请选择贵单位情况**”的选项组中选择 “**连续生产/开工类企事业单位**”
将**第一页**进行**截图**
点击**下一页**按钮
请在第二页填写以下内容


| 栏位     |          内容          |
| :--------- | :----------------------: |
| 申请日期 | 运行脚本的当年元旦日期 |
| 申请人   |         自动化         |
| 联系方式 |       13888888888       |

点击**下一页**按钮
将**第二页**进行**截图**
请在第三页填写以下内容


| 栏位                                                     |      内容      |
| :--------------------------------------------------------- | :--------------: |
| 报备单位                                                 |    测试公司    |
| 在岗人数                                                 |       99       |
| 报备日期                                                 | 执行测试的日期 |
| 湖北籍员工、前往湖北以及与湖北人员密切接触的员工（人数） |       0       |
| 单位负责人                                               |    您的姓名    |
| 联系方式                                                 |  13888888888  |
| 疫情防控方案                                             |    测试内容    |

将**第三页**进行**截图**
点击**提交**按钮
判断此时的提交状态，并进行**截图**

测试完成后，应生成相应的[HTML](https://cucumber.io/docs/cucumber/reporting/)格式的[测试报告](https://cucumber.io/docs/cucumber/reporting/#built-in-reporter-plugins)

---

## 3. 手机APP(Appium)

请基于 [Appium](http://appium.io/)  完成任意一款APP的自动化测试，请附带可下载的测试APP样例链接。或者直接基于本模板项目给定样例APP [src/test/resources/apk/app-debug-1.0.0.apk](https://github.com/alaahong/open_automation_quiz/releases/tag/apk-1.0.0) 完成测试。
测试内容需包括以下操作

* 点击按钮
* 提取资源文本
* 断言不同页面的资源
