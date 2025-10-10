BITS 64

section .data
    filename db "data.txt", 0
    len      equ 7

    msg db "Среднее арифметическое разницы из файла: "
    msg_len equ $ - msg

section .bss
    x resd len
    y resd len
    avg resd 1
    buffer resb 1024

section .text
    global _start

_start:
    mov rax, 2
    mov rdi, filename
    xor rsi, rsi
    xor rdx, rdx
    syscall
    mov rbx, rax

    mov rax, 0
    mov rdi, rbx
    mov rsi, buffer
    mov rdx, 1024
    syscall

    mov rax, 3
    mov rdi, rbx
    syscall

    mov rsi, buffer
    mov rdi, x
    call parse_array
    mov rdi, y
    call parse_array

    mov ecx, len
    mov esi, x
    mov edi, y
    xor ebx, ebx

calculate_diff_and_sum:
    mov eax, [esi]
    sub eax, [edi]
    add ebx, eax
    add esi, 4
    add edi, 4
    loop calculate_diff_and_sum

    mov eax, ebx
    cdq
    mov ecx, len
    idiv ecx
    mov [avg], eax

    mov rax, 1
    mov rdi, 1
    mov rsi, msg
    mov rdx, msg_len
    syscall

    mov eax, [avg]
    call print_int

    mov rax, 60
    xor rdi, rdi
    syscall

parse_array:
    push rdi
    mov ecx, len
.parse_loop:
    call parse_int
    mov [rdi], eax
    add rdi, 4
    loop .parse_loop
    pop rdi
    ret

parse_int:
    xor eax, eax
.skip_delimiters:
    cmp byte [rsi], ' '
    je .next_char
    cmp byte [rsi], 10
    je .next_char
    cmp byte [rsi], 13
    je .next_char
    jmp .start_parsing
.next_char:
    inc rsi
    jmp .skip_delimiters
.start_parsing:
    movzx edx, byte [rsi]
    cmp edx, '0'
    jb .done
    cmp edx, '9'
    ja .done
    sub edx, '0'
    imul eax, 10
    add eax, edx
    inc rsi
    jmp .start_parsing
.done:
    ret

print_int:
    mov rdi, buffer + 1023
    mov byte [rdi], 10
    dec rdi
    mov rbx, 10
    mov rcx, 1
    test eax, eax
    jns .to_ascii_loop
    neg eax
    mov rcx, -1
.to_ascii_loop:
    cmp eax, 0
    je .check_zero
.convert:
    xor edx, edx
    div rbx
    add dl, '0'
    mov [rdi], dl
    dec rdi
    test eax, eax
    jnz .convert
    jmp .check_sign
.check_zero:
    mov byte[rdi], '0'
    dec rdi
.check_sign:
    cmp rcx, 0
    jg .do_print
    mov byte [rdi], '-'
    dec rdi
.do_print:
    inc rdi
    mov rsi, rdi
    mov rdx, buffer + 1024
    sub rdx, rsi
    mov rax, 1
    mov rdi, 1
    syscall
    ret
