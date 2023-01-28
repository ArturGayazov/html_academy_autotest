"""Общий <header> для страниц"""
from uatf import *
from uatf.ui import *
from components import *


@parent_element('.page-header__top')
class Header(Region):
    """Блок <header> страниц
    В большинстве случаев везде одинаковый"""

    start = Dropdown(locator='.main-menu__dropdown [data-nav="start"]', rus_name='Начать с нуля')
    profession = Dropdown(locator='.main-menu__dropdown [data-nav="profession"]', rus_name='Получить профессию')
    grade = Dropdown(locator='.main-menu__dropdown [data-nav="grade"]', rus_name='Повысить грейд')

    @allure.step('Переходим в нужный раздел')
    def select_point_in_start(self, point_name: str, mouse_over: bool = False):
        """Переходим в нужный раздел
        :param point_name: Название раздела
        :param mouse_over: True-открываем список наведением мышки, False-кликом по заголовку списка"""

        self.start.open(mouse_over=mouse_over)
        self.start.select_item(point_name)

    @allure.step('Переходим в нужный раздел')
    def select_point_in_profession(self, point_name: str, mouse_over: bool = False):
        """
        :param point_name: Название раздела
        :param mouse_over: True-открываем список наведением мышки, False-кликом по заголовку списка"""

        self.profession.open(mouse_over=mouse_over)
        self.profession.select_item(point_name)

    @allure.step('Переходим в нужный раздел')
    def select_point_in_grade(self, point_name: str, mouse_over: bool = False):
        """
        :param point_name: Название раздела
        :param mouse_over: True-открываем список наведением мышки, False-кликом по заголовку списка"""

        self.grade.open(mouse_over=mouse_over)
        self.grade.select_item(point_name)

    @allure.step('Проверяем кол-во элементов в выпадающем списке')
    def check_counts_in_dropdown(self, dropdown_name: str, counts: int, mouse_over=False):
        """
        :param dropdown_name: Название выпадающего списка
        :param counts: Кол-во пунктов
        :param mouse_over: True-открываем наведением мышки на заголовок выпадающего списка, False-кликом по
        заголовку списка"""

        dropdown_dict = {
            'Начать с нуля': self.start,
            'Получить профессию': self.profession,
            'Повысить грейд': self.grade
        }
        elem = dropdown_dict.get(dropdown_name)
        elem.open(mouse_over=mouse_over)
        elem.points.should_be(CountElements(counts))
