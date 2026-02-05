JAVA = java
JAVAC = javac
LIBS = libs
SRC = src
OUT = out
CP = -cp $(LIBS)/gson-2.10.1.jar:$(OUT)
CPTEST = -cp $(LIBS)/*:$(OUT)/
JAR_TEST = -jar $(LIBS)/junit-platform-console-standalone-1.10.2.jar

MAIN = $(wildcard $(SRC)/main/java/**/*.java)
TEST = $(wildcard $(SRC)/test/**/*.java)

AS_PATH = aggregation.AggregationServer
CS_PATH = content.ContentServer
C_PATH = client.GETClient

all: build

build:
	$(JAVAC) $(CP) -d $(OUT) $(MAIN)

run-server:
	$(JAVA) $(CP) $(AS_PATH)

run-content-1:
	$(JAVA) $(CP) $(CS_PATH) localhost:4567 src/main/java/content/weather_1.txt

run-content-2:
	$(JAVA) $(CP) $(CS_PATH) localhost:4567 src/main/java/content/weather_2.txt

run-client-1:
	$(JAVA) $(CP) $(C_PATH) localhost:4567 IDS60901

run-client-2:
	$(JAVA) $(CP) $(C_PATH) localhost:4567 IDS96012

compile-test: build
	$(JAVAC) $(CPTEST) -d $(OUT) $(TEST)

integration-test: compile-test
	$(JAVA) $(JAR_TEST) --class-path "out:libs/gson-2.10.1.jar" --select-class IntegrationTest

json-util-test: compile-test
	$(JAVA) $(JAR_TEST) --class-path "out:libs/gson-2.10.1.jar" --select-class JsonUtilTest

lamport-clock-test: compile-test
	$(JAVA) $(JAR_TEST) --class-path "out:libs/gson-2.10.1.jar" --select-class LamportClockTest
