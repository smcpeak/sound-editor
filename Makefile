# sound-edit/Makefile

all:
.PHONY: all


# Eliminate all implicit rules.
.SUFFIXES:

# Delete a target when its recipe fails.
.DELETE_ON_ERROR:

# Do not remove "intermediate" targets.
.SECONDARY:


# Tools.
JAVAC := javac
JAR := jar

# Ensure the directory meant to hold the output file of a recipe exists.
CREATE_OUTPUT_DIRECTORY = @mkdir -p $(dir $@)


# Sources.
JAVA_FILES := $(shell find src -name '*.java')


all: dist/snded.jar
dist/snded.jar: $(JAVA_FILES)
	rm -rf bin
	mkdir -p bin
	$(JAVAC) -sourcepath src -d bin $(JAVA_FILES)
	mkdir -p dist
	cd bin && $(JAR) cfm ../dist/snded.jar ../src/MANIFEST.MF *

.PHONY: check
check: dist/snded.jar
	./snded soft-click.wav info
	./snded soft-click.wav bytes 4
	./snded soft-click.wav samples 4
	./snded soft-click.wav sounds -60 0.0002


.PHONY: clean all check
clean:
	rm -rf bin dist out


# EOF
