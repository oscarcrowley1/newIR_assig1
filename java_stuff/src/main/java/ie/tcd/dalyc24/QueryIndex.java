package ie.tcd.dalyc24;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import java.nio.file.Paths;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.analysis.CharArraySet;

import org.apache.lucene.document.Document;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;

import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;

import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.search.similarities.MultiSimilarity;
import org.apache.lucene.search.IndexSearcher;

import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;

public class QueryIndex
{
    private static String INDEX_DIRECTORY = "../index";
    //private static int MAX_RESULTS = 10;

    private Analyzer analyzer;
    private Directory directory;

    public QueryIndex(String analyzer) throws IOException
    {
        if(analyzer.equals("EnglishAnalyzer")){
            CharArraySet customStopWords = CharArraySet.copy(EnglishAnalyzer.getDefaultStopSet());
            customStopWords.add("b");
            customStopWords.add("i.e");
            System.out.println(customStopWords.toString());
            this.analyzer = new EnglishAnalyzer(customStopWords);
        }
        else if(analyzer.equals("StandardAnalyzer")){
            this.analyzer = new StandardAnalyzer();
        }
        else{
            this.analyzer = new SimpleAnalyzer();
        }
        this.directory = FSDirectory.open(Paths.get(INDEX_DIRECTORY));
    }

    public void buildIndex(String[] args, String analyzerType) throws IOException
    {
        FieldType ft = new FieldType(TextField.TYPE_STORED);
        ft.setTokenized(true);
        ft.setStoreTermVectors(true);
        ft.setStoreTermVectorPositions(true);
        ft.setStoreTermVectorOffsets(true);
        ft.setStoreTermVectorPayloads(true);

        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        IndexWriter iwriter = new IndexWriter(directory, config);  

        for (String arg : args)
        {
            System.out.printf("Indexing \"%s\"\n", arg);

            String inputFile="../cran/cran.all.1400"; 
            BufferedReader br = new BufferedReader(new FileReader(new File(inputFile)));
            String line=null;
            StringBuilder sb = new StringBuilder();
            int count=0;
            //String currentSection = ".I";
            Document doc = new Document();
            
            try {
                while((line = br.readLine()) != null){
                    if(line.startsWith(".I")){
                        // if the line starts with this it means a the past document should be added to the index and a document with this indx numbe should be created
                        if(count!=0){
                            doc.add(new Field("Description", sb.toString(), ft));
                            iwriter.addDocument(doc);

                            doc.removeField("Number");
                            doc.removeField("Title");
                            doc.removeField("Author");
                            doc.removeField("Journal");
                            doc.removeField("Description");
                        }
                        String numberFromLine = line.substring(3, line.length());
                        doc.add(new Field("Number", numberFromLine, ft));
                        //currentSection = ".I";
                    }
                    else if(line.startsWith(".T")){//if the line starts with one of these then the serial buffer should be dded to the last field and a new buffer be created for the field in question
                        //currentSection = ".T";
                        sb.delete(0, sb.length());
                    }
                    else if(line.startsWith(".A")){
                        doc.add(new Field("Title", sb.toString(), ft));
                        //currentSection = ".A";
                        sb.delete(0, sb.length());
                    }
                    else if(line.startsWith(".B")){
                        doc.add(new Field("Author", sb.toString(), ft));
                        //currentSection = ".B";
                        sb.delete(0, sb.length());
                    }
                    else if(line.startsWith(".W")){
                        doc.add(new Field("Journal", sb.toString(), ft));
                        //currentSection = ".W";
                        sb.delete(0, sb.length());
                    }
                    else{
                        if(sb.length()!=0){//if not the start of a new field then we should just add the line to the serial buffer
                            sb.append(" ");
                        }
                        sb.append(line);
                    }
                    count++;
                }

            } catch (Exception ex) {
                ex.printStackTrace();
            }
            finally {
                br.close();
            }
        }
        iwriter.close();
    }

    public void shutdown() throws IOException
    {
        directory.close();
    }

    public void cranQueries(String similarityType, String outputFileName, String analyzerType) throws IOException, ParseException
	{
        Similarity simModel;
        if(similarityType.equals("BM25Similarity")){
            simModel = new BM25Similarity();
        }
        else if(similarityType.equals("ClassicSimilarity")){
            simModel = new ClassicSimilarity();
        }
        else{
            Similarity[] simArray = {new BM25Similarity(), new ClassicSimilarity()};
            simModel = new MultiSimilarity(simArray);
        }

        File outputFile = new File(outputFileName);

        if (outputFile.createNewFile()) {
            System.out.println("File created: " + outputFile.getName());
        } else {
            System.out.println("File already exists.");
        }
        FileWriter outputWriter = new FileWriter(outputFileName);

        String inputFile="../cran/cran.qry"; 
        BufferedReader br = new BufferedReader(new FileReader(new File(inputFile)));
        String line=null;
        StringBuilder sb = new StringBuilder();
        int count=1;
        //String currentSection = ".I";
        //Document doc = new Document();
        //String queryNumber;
        int outputQN=1;
        String queryText;
        //int stopcounter = 1;
        
        try {
            while((line = br.readLine()) != null){
                if(line.startsWith(".I")){
                    // the query index is taken in but does not represent the true index which is measured with the count variable
                    // the current serial uffer is then parsed into our query
                    if(count!=1){
                        queryText = sb.toString();
                        DirectoryReader ireader = DirectoryReader.open(directory);
                        IndexSearcher isearcher = new IndexSearcher(ireader);
                        
                        isearcher.setSimilarity(simModel);
                        String[] fieldString = { "Title", "Description"};
                        QueryParser parser = new MultiFieldQueryParser(fieldString, analyzer);
                        String queryString = "";
                        queryString = queryText.trim();
                        queryString = queryString.replace("?", "");// this part strips out unnecessary symbols forom the query string
                        queryString = queryString.replace("*", "");
                        queryString = queryString.replace("/", "");

                        if (queryString.length() > 0)
                        {
                            Query query = parser.parse(queryString);
                            ScoreDoc[] hits = isearcher.search(query, 1400).scoreDocs;
                            for (int i = 0; i < hits.length; i++)
                            {
                                Document hitDoc = isearcher.doc(hits[i].doc);
                                outputWriter.write(outputQN + " x " + hitDoc.get("Number") + " " + i + " " + hits[i].score + " y\n");
                                // this outputs the query number, document number, doument number order by relevance, and relevance score
                            }
                        }
                        outputQN++;
                    }
                    //queryNumber = line;
                    //currentSection = ".I";
                }
                else if(line.startsWith(".W")){
                    //currentSection = ".W";
                    sb.delete(0, sb.length());
                }
                else{
                    if(sb.length()!=0){
                        sb.append(" ");
                    }
                    sb.append(line);
                }
                count++;
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {}
        br.close();
        outputWriter.close();
        System.out.println("Successfully wrote to the file.");
	}

    public static void main(String[] args) throws IOException, ParseException
    {
        
        if (args.length <= 0)
        {
            System.out.println("Expected corpus as input");
            System.exit(1);            
        }

        String[] analyzersArray = {"EnglishAnalyzer", "StandardAnalyzer", "SimpleAnalyzer"};
        String[] similaritiesArray = {"BM25Similarity", "ClassicSimilarity", "BooleanSimilarity"};
        String fileSuffix = "Output.txt";

        QueryIndex qi = new QueryIndex(analyzersArray[1]);

        for(int i = 0; i < analyzersArray.length; i++){
            //first we cycle through the analyzer types
            String analyserString = analyzersArray[i];
            qi =  new QueryIndex(analyserString);
            qi.buildIndex(args, analyserString);
            for(int j = 0; j < similaritiesArray.length; j++){
                //then we cycle through similarities making nine output files in total
                String similarityString = similaritiesArray[j];
                String txtName = analyserString + similarityString + fileSuffix;
                System.out.println("Creating: " + txtName);
                qi.cranQueries(similarityString, txtName, analyserString);
            }
        }
        qi.shutdown();
    }
}
