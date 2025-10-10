section .data
    x dd 5, 3, 2, 6, 1, 7, 4
    y dd 0, 10, 1, 9, 2, 8, 5
    len equ ($ - y) / 4

    msg db "Среднее арифметическое разницы: "
    msg_len equ $ - msg

section .bss
    avg resd 1
    buffer resb 12

section .text
    global _start

_start:
    mov ecx, len       ; количество элементов
    mov esi, x         ; массив x
    mov edi, y         ; массив y
    xor ebx, ebx

calculate_diff_and_sum:
    mov eax, [esi]     ; загрузка элемента из x
    sub eax, [edi]     ; вычитание элемента из y
    add ebx, eax       ; добавление разницы к сумме

    add esi, 4         ; переход к следующему элементу
    add edi, 4
    loop calculate_diff_and_sum

    mov eax, ebx
    cdq
    mov ecx, len
    idiv ecx
    mov [avg], eax

    mov eax, 1
    mov edi, 1
    mov esi, msg
    mov edx, msg_len
    syscall

    mov eax, [avg]
    call print_int

    mov eax, 60
    xor edi, edi
    syscall

print_int:
    mov rdi, buffer + 10
    mov byte [rdi], 10
    dec rdi
    mov rbx, 10
    mov rcx, 1           ; 1 для положительных, -1 для отрицательных

    test eax, eax
    jns .to_ascii_loop
    mov rcx, -1
    neg eax

.to_ascii_loop:
    cmp eax, 0
    je .check_zero       ; обработаем отдельно 0
.convert:
    xor edx, edx
    div rbx
    add dl, '0'          ; в ASCII
    mov [rdi], dl        ; в буфер
    dec rdi
    test eax, eax
    jnz .convert
    jmp .check_sign

.check_zero:
    mov byte[rdi], '0'
    dec rdi

.check_sign:
    test rcx, rcx
    jns .do_print
    mov byte [rdi], '-'
    dec rdi

.do_print:
    inc rdi
    mov rsi, rdi
    mov rdx, buffer + 11
    sub rdx, rsi
    mov rax, 1
    mov rdi, 1
    syscall
    ret
