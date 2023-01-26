import allure
import pytest


class TestExample(object):

    def test_success(self):
        """Должен пройти"""
        assert True == True

    def test_failed(self):
        """Должен упасть"""
        assert True == False

    def test_skip(self):
        """Должен быть пропущен"""
        pytest.skip('Не запускать')

    def test_broken(self):
        """Ощибка в коде"""
        raise Exception('oops')


class TestExampleXfail(object):

    @pytest.mark.xfail(condition=lambda: True, reason='Тест упадет так как True != False')
    def test_xfail_expected_failure(self):
        """Тетс помечен фикстурой ожидания падения"""
        assert True == False

    @pytest.mark.xfail(condition=lambda: True, reason='Тест упадет так как True != False')
    def test_xfail_unexpected_pass(self):
        """Тест помеяен фикстурой ожидания падений, но он не упадет так как сравнение верное"""
        assert True != False


class TestExampleSkipIf(object):

    @pytest.mark.skipif('2 + 2 != 5', reason='Тест не будет запущен так как условие верно @pytest.mark.skipif')
    def test_skip_by_triggered_condition(self):
        pass


class TestExampleParametrize(object):

    @allure.step
    def simple_step(self, step_param1, step_param2=None):
        """Какой-то метод который принимает один обязательный и один необязательный параметр"""
        pass

    @pytest.mark.parametrize('param1', [True, False], ids=['id explaining value 1', 'id explaining value 2'])
    def test_parameterize_with_id(self, param1):
        self.simple_step(param1)

    @pytest.mark.parametrize('param1', [True, False])
    @pytest.mark.parametrize('param2', ['value 1', 'value 2'])
    def test_parametrize_with_two_parameters(self, param1, param2):
        self.simple_step(param1, param2)

    @pytest.mark.parametrize('param1', [True], ids=['boolean parameter id'])
    @pytest.mark.parametrize('param2', ['value 1', 'value 2'])
    @pytest.mark.parametrize('param3', [1])
    def test_parameterize_with_uneven_value_sets(self, param1, param2, param3):
        self.simple_step(param1, param3)
        self.simple_step(param2)


class TestExampleSteps(object):
    """Примеры пошагового представления теста"""

    @allure.step
    def open_search_dialog(self):
        """Открывает диалог поиска"""
        print('Открыли диалог поиска')

    @allure.step
    def search(self, name):
        """Поиск"""
        print(f'Выполнили поиск {name}')

    @allure.step
    def select_item(self, name):
        """Выбираем найденное значение"""
        print(f'Выбрали найденное значение {name}')

    @allure.step('А тут можно указать что делает метод: "Ищем необходимое значение"')
    def search_name(self, name):
        """Ищем необходимое значение"""
        self.open_search_dialog()
        self.search(name)
        self.select_item(name)

    def test_check_steps(self):
        """Проверяем вложенности"""
        self.search_name('Нимфорт')


class TestExampleAttachFile(object):  # если в будущем будем сравнивать какие-то эталоны можно заюзать это
    # !!! а еще можно сравнивать html страницы

    def test_compare_two_file(self):
        allure.attach.file('test_allure_example/test_file.txt', 'Эталонный файл', allure.attachment_type.TEXT)
        print('Сравниваем два файла')
        file_1 = True
        file_2 = False
        assert file_1 == file_2


class TestExampleDescription(object):

    @allure.description("""Описание теста с использованием фикстуры '@allure.description()'""")
    def test_check_description_fixture(self):
        pass

    def test_check_description_in_test(self):
        """Описание тесты без использования фикстуры '@allure.description()'"""
        pass


class TestExampleTitle(object):

    @allure.title('Название АТ (тут можно вставить название чек листа)')
    def test_check_title(self):
        pass


class TestExampleLink(object):

    @allure.link('сюда можно засунуть ссылку на чек-лист')
    def test_check_link(self):
        pass
