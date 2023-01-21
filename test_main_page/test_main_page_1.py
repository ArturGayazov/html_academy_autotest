"""Пробный тест"""
import pytest
from uatf import *
from uatf.ui import *
from pages.main import MainPage


class TestOpenSite(TestCaseUI):
    """Пробный тест"""

    @classmethod
    def setUpClass(cls):
        cls.main = MainPage(cls.driver)

    def setUp(self):

        log('Переходим на главную')
        self.main.open()

    @pytest.mark.parametrize('dropdown_name,counts', (
            ['Начать с нуля', 8],
            ['Получить профессию', 10],
            ['Повысить грейд', 10]
    ))
    def test_01_check_points_in_dropdown(self, dropdown_name, counts):
        """Проверяем кол-во элементов в выпадающих списках заголовка"""

        log('Открываем выпадающий список кликом по заголовку')
        self.main.header.check_counts_in_dropdown(dropdown_name, counts)

        log('Открываем выпадающий список наведением мышки на заголовок')
        self.browser.refresh()
        self.main.header.check_counts_in_dropdown(dropdown_name, counts, True)

    def tearDown(self):
        self.browser.close_windows_and_alert()
