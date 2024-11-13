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
JAVA := java
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
	$(JAVA) -cp bin snded.FFTTest
	./snded test-data/soft-click.wav info
	./snded test-data/soft-click.wav bytes max:4
	./snded test-data/soft-click.wav samples max:4
	./snded test-data/soft-click.wav sounds loud_dB:-60 close_s:0.0002 duration_s:0.0005
	mkdir -p out
	./snded test-data/soft-click.wav declick out:out/soft-click-declick.wav loud_dB:-60 close_s:0.0002 duration_s:0.0005
	./snded test-data/sine-440hz.wav freqBins
	./snded test-data/sine-440hz-and-4000hz.wav freqBins


.PHONY: clean all check
clean:
	rm -rf bin dist out


# EOF
