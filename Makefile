CATW = node_modules/.bin/catw
LESSC = node_modules/.bin/lessc

MAIN_LESS = src/index.less

all: build

$(CATW):
	npm install

$(LESSC):
	npm install

dist:
	mkdir dist

clean:
	rm -rf dist

copy: dist
	cp src/index.html dist/index.html

build_less: $(LESSC) dist copy
	$(LESSC) $(MAIN_LESS) > dist/bundle.css

build_cljs: dist copy
	lein cljsbuild once dev

watch_less: $(CATW) dist copy
	$(CATW) -c '$(LESSC) $(MAIN_LESS)' 'src/**/*.less' -o dist/bundle.css -v

build: clean build_less build_cljs

watch: clean watch_less

.PHONY: clean build copy watch watch_less build_less
