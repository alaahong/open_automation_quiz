


##考研帮APP 基于手机APP自动化测试


import smtplib
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart
import os
import time
def sendMail(report_file):

	msg = MIMEMultipart()
	file=open(report_file,'rb').read()
	att1=MIMEText(file,_charset='utf-8')
	att1["Content-Type"] = 'application/octet-stream'

	att1["Content-Disposition"] = 'attachment; filename="test_report.html"'
	msg.attach(att1)


	sender = 'my224102@163.com'

	password = 'xiaomei123'

	receiver = ['404881586@qq.com','my224102@163.com']

	smtp_server = 'smtp.163.com'

	msg['From'] = sender

	msg['To'] = ','.join(receiver)

	msg['Subject'] = '考研帮自动化测试结果-'+time.strftime("%Y-%m-%d %H_%M_%S")
	server = smtplib.SMTP(smtp_server,25)

	server.login(sender,password)
	server.sendmail(sender,receiver,msg.as_string())
	server.quit()