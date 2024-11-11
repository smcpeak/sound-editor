# sound-edit/Makefile

all: dist/snded.jar

JAVAC := javac
JAR := jar

JAVA_FILES := $(shell find src -name '*.java')

# Ensure the directory meant to hold the output file of a recipe exists.
CREATE_OUTPUT_DIRECTORY = @mkdir -p $(dir $@)


# Eliminate all implicit rules.
.SUFFIXES:

# Delete a target when its recipe fails.
.DELETE_ON_ERROR:

# Do not remove "intermediate" targets.
.SECONDARY:


dist/snded.jar: $(JAVA_FILES)
	rm -rf bin
	mkdir -p bin
	$(JAVAC) -sourcepath src -d bin $(JAVA_FILES)
	mkdir -p dist
	cd bin && $(JAR) cfm ../dist/snded.jar ../src/MANIFEST.MF *

.PHONY: clean all check
clean:
	rm -rf bin dist out


# EOF
