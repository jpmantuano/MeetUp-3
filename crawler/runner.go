package main

import (
	"fmt"
	"github.com/gocolly/colly"
	"io"
	"log"
	"net/http"
	"os"
	"regexp"
	"strings"
)

var file_location = "./files/"

func main() {
	c := colly.NewCollector(
		colly.AllowedDomains("data.gov.ph"),
		colly.URLFilters(
			regexp.MustCompile("dataset"),
			regexp.MustCompile("csv-7"),
		),
		colly.CacheDir("./main_Collector"),
		colly.MaxDepth(10),
	)

	// Create another collector to scrape course details
	detailCollector := colly.NewCollector(
		colly.AllowedDomains("data.gov.ph"),
		colly.URLFilters(
			regexp.MustCompile("dataset"),
			//regexp.MustCompile(".csv"),
		),
		colly.CacheDir("./details_collector"),
		colly.MaxDepth(10),
	)

	// On every a element which has href attribute call callback
	c.OnHTML("a[href]", func(e *colly.HTMLElement) {
		link := e.Attr("href")
		// Print link
		//fmt.Printf("Link found: %q -> %s\n", e.Text, link)
		// Visit link found on page
		// Only those links are visited which are matched by  any of the URLFilter regexps
		if strings.Index(link, "sort_by=changed") > -1 {
			return
		}

		absoluteURL := e.Request.AbsoluteURL(link)

		c.Visit(absoluteURL)
	})

	c.OnHTML("a[title]", func(e *colly.HTMLElement) {
		link := e.Attr("href")
		// Print link
		//fmt.Printf("Link found: %q -> %s\n", e.Text, link)
		// Visit link found on page
		// Only those links are visited which are matched by  any of the URLFilter regexps
		//if strings.Index(link, "sort_by=changed") > -1 {
		//	return
		//}

		title := e.Text
		fmt.Printf("Title: %s\n", title)

		absoluteURL := e.Request.AbsoluteURL(link)

		if len(title) > 0 && !strings.Contains(absoluteURL, "search") {
			detailCollector.Visit(absoluteURL)
		}
	})

	c.OnRequest(func(r *colly.Request) {
		fmt.Println("Visiting", r.URL.String())
	})

	// Extract details of the course
	detailCollector.OnHTML(`a[data-format=csv]`, func(e *colly.HTMLElement) {
		log.Println("Course found", e.Request.URL)
		//title := e.ChildText(".course-title")
		//if title == "" {
		//	log.Println("No title found", e.Request.URL)
		//}

		// Iterate over rows of the table which contains different information
		// about the course
		//e.ForEach("h2.pane-title", func(_ int, el *colly.HTMLElement) {
		//	fmt.Println(el.ChildText(""))
		//})

		file_url := e.Attr("href")
		fmt.Println(file_url)
		title := e.Attr("title")
		fmt.Println(title)

		os.MkdirAll(file_location, os.ModePerm)

		if err := DownloadFile(title, file_url); err != nil {
			panic(err)
		}

	})

	c.Visit("https://data.gov.ph/search/type/dataset")
}

func DownloadFile(filepath string, url string) error {
	// Get the data
	resp, err := http.Get(url)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	// Create the file
	out, err := os.Create(file_location + filepath)
	if err != nil {
		return err
	}
	defer out.Close()

	// Write the body to file
	_, err = io.Copy(out, resp.Body)
	return err
}
