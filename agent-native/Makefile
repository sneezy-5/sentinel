CC ?= gcc
CFLAGS ?= -Wall -Wextra -O2 -Iinclude
BIN_DIR := bin
TARGET := $(BIN_DIR)/sentinel-native

SRCS := src/main.c src/system_stats.c src/process_stats.c
OBJS := $(SRCS:src/%.c=$(BIN_DIR)/%.o)

.PHONY: all clean

all: $(TARGET)

$(TARGET): $(OBJS)
	$(CC) $(OBJS) -o $(TARGET)

$(BIN_DIR)/%.o: src/%.c | $(BIN_DIR)
	$(CC) $(CFLAGS) -c $< -o $@

$(BIN_DIR):
	mkdir -p $(BIN_DIR)

clean:
	rm -rf $(BIN_DIR)
