def analyze_file(filename):
    try:
        with open(filename, 'r', encoding='utf-8') as file:
            content = file.read()
            
        lines = content.splitlines()
        line_count = len(lines)
        
        char_count = len(content)
        
        empty_lines = sum(1 for line in lines if not line.strip())
        
        char_freq = {}
        for char in content:
            char_freq[char] = char_freq.get(char, 0) + 1
        
        return {
            'lines': line_count,
            'chars': char_count,
            'empty_lines': empty_lines,
            'char_frequency': char_freq
        }
    
    except FileNotFoundError:
        print(f'Ошибка: Файл `{filename}` не найден.')
        return None
    except Exception as e:
        print(f'Ошибка при чтении файла: {e}')
        return None

def display_menu():
    print('\n' + '=' * 5)
    print('МЕНЮ АНАЛИЗА ФАЙЛА')
    print('=' * 5)
    print('Выберите опции для отображения:')
    print('1. Количество строк')
    print('2. Количество символов')
    print('3. Количество пустых строк')
    print('4. Частотный словарь символов')
    print('5. Все показатели')
    print('6. Выйти')

def get_user_choices():
    choices = []
    while True:
        display_menu()
        try:
            choice = input('\nВведите номера опций через запятую: ').strip()
            
            if choice.lower() in ['6', 'выход']:
                return None
                
            if not choice:
                print('Пожалуйста, выберите хотя бы одну опцию.')
                continue
                
            selected = [int(x.strip()) for x in choice.split(',') if x.strip().isdigit()]
            
            valid_options = [1, 2, 3, 4, 5]
            if not all(opt in valid_options for opt in selected):
                print('Некорректный выбор. Пожалуйста, используйте числа от 1 до 5.')
                continue
                
            if 5 in selected:
                choices = [1, 2, 3, 4]
            else:
                choices = selected
                
            return choices
            
        except ValueError:
            print('Пожалуйста, введите корректные номера опций.')

def display_results(results, choices):
    print('\n' + '=' * 5)
    print('РЕЗУЛЬТАТЫ АНАЛИЗА')
    print('=' * 5)
    
    if 1 in choices or 5 in choices:
        print(f"Количество строк: {results['lines']}")
    
    if 2 in choices or 5 in choices:
        print(f"Количество символов: {results['chars']}")
    
    if 3 in choices or 5 in choices:
        print(f"Количество пустых строк: {results['empty_lines']}")
    
    if 4 in choices or 5 in choices:
        print('\nЧастотный словарь символов:')
        sorted_chars = sorted(results['char_frequency'].items(), key=lambda x: x[1], reverse=True)
        
        for i, (char, freq) in enumerate(sorted_chars[:30]):
            if char == '\n':
                print(f'  [перевод строки]: {freq}')
            elif char == ' ':
                print(f'  [пробел]: {freq}')
            else:
                print(f'  `{char}`: {freq}')
        
        if len(sorted_chars) > 30:
            print(f'  ... и еще {len(sorted_chars) - 30} символов')

def main():
    print('Программа анализа текстового файла')
    print('-' * 3)
    
    filename = input('Введите имя файла для анализа: ').strip()
    
    if not filename:
        print('Имя файла не может быть пустым.')
        return
    
    results = analyze_file(filename)
    
    if results is None:
        return
    
    choices = get_user_choices()
    
    if choices is None:
        print('Программа завершена.')
        return
    
    display_results(results, choices)
    
    while True:
        continue_choice = input('\nХотите проанализировать другой файл? (Y/n): ').strip().lower()
        if continue_choice == 'y':
            main()
            break
        elif continue_choice == 'n':
            print('Программа завершена.')
            break
        else:
            main()

if __name__ == '__main__':
    main()
