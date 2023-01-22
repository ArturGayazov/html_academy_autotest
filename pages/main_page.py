from uatf import *
from uatf.ui import *
from components import *
from blocks.header import *


class MainPage(Region):
    """Главная страница"""

    header = Header()

    def open(self):
        """Открываем главную страницу"""

        self.browser.open('https://htmlacademy.ru/')
