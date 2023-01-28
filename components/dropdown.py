from uatf import *
from uatf.ui import *
import allure


class Dropdown(Control):
    """Выпадающий список"""

    def __init__(self, how=By.CSS_SELECTOR, locator='.main-menu__item--dropdown',
                 rus_name='выпадающий список'):
        super().__init__(how, locator, rus_name)

        self.header_link = Element(
            how=By.CSS_SELECTOR,
            locator='.main-menu__link ',
            rus_name='Заголовок выпадающего списка')

        self.points = CustomList(
            how=By.CSS_SELECTOR,
            locator='.main-menu__item a',
            rus_name="Пункты"
        )

        self.group_points = CustomList(
            how=By.CSS_SELECTOR,
            locator='.main-menu__item span',
            rus_name="Группа пунктов"
        )

    @allure.step('Открываем выпадающий список')
    def open(self, mouse_over: bool = False):
        """
        :param mouse_over: True-открываем наведением мышки на заголовок выпадающего списка, False-кликом по
        заголовку списка"""

        self.header_link.mouse_over() if mouse_over else self.header_link.click()

    @property
    def text(self):
        """Получаем текст заголовка выпадающего списка"""

        return self.header_link.text

    @property
    def count_elements(self):
        """Возвращаем кол-во пунктов в выпадающем списке"""

        return self.points.count_elements

    @allure.step('Проваливаемся в необходимый пункт')
    def select_item(self, point_name: str):
        """
        :param point_name: Название пункта"""

        self.points.item(contains_text=point_name).click()
