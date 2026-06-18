#!/bin/bash
# Test various escape sequences used by Claude CLI

# Test 1: DECSET/DECRST multi-mode
printf 'Test 1: DECSET/DECRST\n'
printf '\033[?25l'  # Hide cursor
sleep 0.1
printf '\033[?25h'  # Show cursor
sleep 0.1

# Test 2: OSC sequences
printf 'Test 2: OSC title\n'
printf '\033]0;Test Title\007'  # Set window title
sleep 0.1

# Test 3: Alternate screen buffer
printf 'Test 3: Alt screen\n'
printf '\033[?1049h'  # Enter alt screen
printf 'This should be on alt screen\n'
sleep 0.5
printf '\033[?1049l'  # Exit alt screen
printf 'Back to main screen\n'

# Test 4: Color sequences
printf '\033[31mRed text\033[0m\n'
printf '\033[42mGreen background\033[0m\n'
printf '\033[1;34mBold blue\033[0m\n'

printf '\nAll tests completed\n'
