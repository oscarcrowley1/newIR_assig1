#!/usr/bin/bash

echo "Hello, $(whoami)!"

cd java_stuff

mvn package

java -jar target/oscar-1.1.jar ../cran/cran.all.1400

cd ../trec_eval

analyzersArray=('EnglishAnalyzer' 'StandardAnalyzer' 'SimpleAnalyzer')
similaritiesArray=('BM25Similarity' 'ClassicSimilarity' 'BooleanSimilarity')
fileSuffix='Output.txt'
fileName=''

#the for loops allow us to check each output file created

for analyzer in "${analyzersArray[@]}"; do
	for similarity in "${similaritiesArray[@]}"; do
		fileName="${analyzer}${similarity}${fileSuffix}"
		echo ${fileName}
		./trec_eval -m official ../cran/QRelsCorrectedforTRECeval ../java_stuff/${fileName}
		echo 
	done
done


