

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.*;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Simple command-line based search demo.
 */
public class SearchFiles {

    private SearchFiles() {
    }

    /**
     * Simple command-line based search demo.
     */
    public static void main(String[] args) throws Exception {
        Map<String, String> consultas = new HashMap<>();


        String usage =
                "Usage:\tjava org.apache.lucene.demo.SearchFiles [-index dir] [-field f] [-repeat n] [-queries file] [-query string] [-raw] [-paging hitsPerPage]\n\nSee http://lucene.apache.org/core/4_1_0/demo/ for details.";
        if (args.length > 0 && ("-h".equals(args[0]) || "-help".equals(args[0]))) {
            System.out.println(usage);
            System.exit(0);
        }

        String index = "index";
        String infoNeeds;
        String output;
        String field = "contents";
        String queries = null;
        int repeat = 0;
        boolean raw = false;
        String queryString = null;
        int hitsPerPage = 10;

        for (int i = 0; i < args.length; i++) {
            if ("-index".equals(args[i])) {
                index = args[i + 1];
                i++;
            } else if ("-infoNeeds".equals(args[i])) {
                infoNeeds = args[i + 1];
                i++;
            } else if ("-output".equals(args[i])) {
                output = args[i + 1];
                i++;
            } else if ("-field".equals(args[i])) {
                field = args[i + 1];
                i++;
            } else if ("-queries".equals(args[i])) {
                queries = args[i + 1];
                i++;

            } else if ("-query".equals(args[i])) {
                queryString = args[i + 1];
                i++;
            } else if ("-repeat".equals(args[i])) {
                repeat = Integer.parseInt(args[i + 1]);
                i++;
            } else if ("-raw".equals(args[i])) {
                raw = true;
            } else if ("-paging".equals(args[i])) {
                hitsPerPage = Integer.parseInt(args[i + 1]);
                if (hitsPerPage <= 0) {
                    System.err.println("There must be at least 1 hit per page.");
                    System.exit(1);
                }
                i++;
            }
        }

        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(index)));
        IndexSearcher searcher = new IndexSearcher(reader);
        Analyzer analyzer = new StandardAnalyzer();

        BufferedReader in = null;
        if (queries != null) {
            in = new BufferedReader(new InputStreamReader(new FileInputStream(queries), "UTF-8"));
        } else {
            in = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
        }
        QueryParser titleParser = new QueryParser("title", analyzer);
        QueryParser descriptionParser = new QueryParser("description", analyzer);
        QueryParser dateParser = new QueryParser("date", analyzer);
        QueryParser subjectParser = new QueryParser("subject", analyzer);
        QueryParser typeParser = new QueryParser("type", analyzer);
        QueryParser creatorParser = new QueryParser("creator", analyzer);


        /**
         * Cogemos las consultas del fichero XML infoNeeds
         */

        File inputFile = new File("infoNeeds.xml");
        DocumentBuilderFactory aux = DocumentBuilderFactory.newInstance();
        DocumentBuilder parseador = aux.newDocumentBuilder();

        org.w3c.dom.Document parserXML = parseador.parse(inputFile);

        NodeList textos = parserXML.getElementsByTagName("text");
        NodeList identificadores = parserXML.getElementsByTagName("identifier");


        for (int i = 0; i < textos.getLength(); i++) {
            Node texto = textos.item(i).getChildNodes().item(0);
            Node identificador = identificadores.item(i).getChildNodes().item(0);

            consultas.put(identificador.getNodeValue(), texto.getNodeValue());
        }

        for (Map.Entry<String,String> entry : consultas.entrySet()){
            System.out.println(entry.getKey()+" " +entry.getValue());
        }




        while (true) {
            if (queries == null && queryString == null) {                        // prompt the user
                System.out.println("Enter query: ");
            }

            String line = queryString != null ? queryString : in.readLine();

            if (line == null || line.length() == -1) {
                break;
            }

            line = line.trim();
            if (line.length() == 0) {
                break;
            }

            String anyo = "[0-9]{4}";
            Pattern patronIntervalo = Pattern.compile(anyo + "-" + anyo + "|" + anyo + " y " + anyo);
            Pattern patronUltimos = Pattern.compile("últimos [0-9]+ años");
            Matcher matcherIntervalo = patronIntervalo.matcher(line);
            Matcher matcherUltimos = patronUltimos.matcher(line);
            BooleanQuery query = null;
            if(matcherIntervalo.find()) {

                Query queryTitulo = titleParser.parse(line);
                BoostQuery queryTituloFin = new BoostQuery(queryTitulo,2);
                Query querySubject = subjectParser.parse(line);
                BoostQuery querySubjectFin = new BoostQuery(querySubject,1.5f);
                Query queryDescription = descriptionParser.parse(line);
                BoostQuery queryDescriptionFin = new BoostQuery(querySubject,1);
                String fInicio = matcherIntervalo.group().substring(0, 4);
                String fFin = matcherIntervalo.group().substring(matcherIntervalo.group().length()-4, matcherIntervalo.group().length());
                Query queryIntervalo = TermRangeQuery.newStringRange("date", fInicio, fFin, true, true);
                query = new BooleanQuery.Builder()
                        .add(queryTituloFin, BooleanClause.Occur.SHOULD)
                        .add(querySubjectFin, BooleanClause.Occur.SHOULD)
                        .add(queryDescriptionFin, BooleanClause.Occur.SHOULD)
                        .add(queryIntervalo, BooleanClause.Occur.MUST)
                        .build();
            }else if(matcherUltimos.find()) {

                Query query2 = dateParser.parse(line);
                BoostQuery query4 = new BoostQuery(query2,1);
                BoostQuery query3 = new BoostQuery(query2,2);
                String fInicio = matcherUltimos.group().substring(0, 4);
                String fFin = matcherUltimos.group().substring(matcherUltimos.group().length()-4, matcherUltimos.group().length());
                Query q35 = TermRangeQuery.newStringRange("date", fInicio, fFin, true, true);
                query = new BooleanQuery.Builder()
                        .add(query4, BooleanClause.Occur.SHOULD)
                        .add(query3, BooleanClause.Occur.SHOULD)
                        .add(q35, BooleanClause.Occur.MUST)
                        .build();
            }else if(matcherIntervalo.find()) {

                Query query2 = parser.parse(line);
                BoostQuery query4 = new BoostQuery(query2,1);
                BoostQuery query3 = new BoostQuery(query2,2);
                String fInicio = matcherIntervalo.group().substring(0, 4);
                String fFin = matcherIntervalo.group().substring(matcherIntervalo.group().length()-4, matcherIntervalo.group().length());
                Query q35 = TermRangeQuery.newStringRange("date", fInicio, fFin, true, true);
                query = new BooleanQuery.Builder()
                        .add(query4, BooleanClause.Occur.SHOULD)
                        .add(query3, BooleanClause.Occur.SHOULD)
                        .add(q35, BooleanClause.Occur.MUST)
                        .build();
            }
            else{
                Query query2 = parser.parse(line);
                BoostQuery query4 = new BoostQuery(query2,1);

                BoostQuery query3 = new BoostQuery(query2,2);
                query = new BooleanQuery.Builder()
                        .add(query4, BooleanClause.Occur.SHOULD)
                        .add(query3, BooleanClause.Occur.SHOULD)
                        .build();
            }


            System.out.println("Searching for: " + query.toString(field));

            if (repeat > 0) {                           // repeat & time as benchmark
                Date start = new Date();
                for (int i = 0; i < repeat; i++) {
                    searcher.search(query, 100);
                }
                Date end = new Date();
                System.out.println("Time: " + (end.getTime() - start.getTime()) + "ms");
            }

//            for(Map.Entry<String, String> entry : consultas.entrySet()){
//                doPagingSearch(entry.getValue(),
//                        searcher, query, hitsPerPage, raw,  queries == null && queryString == null);
//            }

            //doPagingSearch(in, searcher, query, hitsPerPage, raw, queries == null && queryString == null);

            if (queryString != null) {
                break;
            }
        }
        reader.close();
    }

    /**
     * This demonstrates a typical paging search scenario, where the search engine presents
     * pages of size n to the user. The user can then go to the next page if interested in
     * the next hits.
     * <p>
     * When the query is executed for the first time, then only enough results are collected
     * to fill 5 result pages. If the user wants to page beyond this limit, then the query
     * is executed another time and all hits are collected.
     */
    public static void doPagingSearch(BufferedReader in, IndexSearcher searcher, Query query,
                                      int hitsPerPage, boolean raw, boolean interactive) throws IOException {

        // Collect enough docs to show 5 pages
        TopDocs results = searcher.search(query, 5 * hitsPerPage);
        ScoreDoc[] hits = results.scoreDocs;

        int numTotalHits = (int) results.totalHits;
        System.out.println(numTotalHits + " total matching documents");

        int start = 0;
        int end = Math.min(numTotalHits, hitsPerPage);

        while (true) {
            if (end > hits.length) {
                System.out.println("Only results 1 - " + hits.length + " of " + numTotalHits + " total matching documents collected.");
                System.out.println("Collect more (y/n) ?");
                String line = in.readLine();
                if (line.length() == 0 || line.charAt(0) == 'n') {
                    break;
                }

                hits = searcher.search(query, numTotalHits).scoreDocs;
            }

            end = Math.min(hits.length, start + hitsPerPage);

            for (int i = start; i < end; i++) {
                if (raw) {                              // output raw format
                    System.out.println("doc=" + hits[i].doc + " score=" + hits[i].score);
                    continue;
                }

                Document doc = searcher.doc(hits[i].doc);
                String path = doc.get("path");
                if (path != null) {
                    System.out.println((i + 1) + ". " + path);
                } else {
                    System.out.println((i + 1) + ". " + "No path for this document");
                }

            }

            if (!interactive || end == 0) {
                break;
            }

            if (numTotalHits >= end) {
                boolean quit = false;
                while (true) {
                    System.out.print("Press ");
                    if (start - hitsPerPage >= 0) {
                        System.out.print("(p)revious page, ");
                    }
                    if (start + hitsPerPage < numTotalHits) {
                        System.out.print("(n)ext page, ");
                    }
                    System.out.println("(q)uit or enter number to jump to a page.");

                    String line = in.readLine();
                    if (line.length() == 0 || line.charAt(0) == 'q') {
                        quit = true;
                        break;
                    }
                    if (line.charAt(0) == 'p') {
                        start = Math.max(0, start - hitsPerPage);
                        break;
                    } else if (line.charAt(0) == 'n') {
                        if (start + hitsPerPage < numTotalHits) {
                            start += hitsPerPage;
                        }
                        break;
                    } else {
                        int page = Integer.parseInt(line);
                        if ((page - 1) * hitsPerPage < numTotalHits) {
                            start = (page - 1) * hitsPerPage;
                            break;
                        } else {
                            System.out.println("No such page");
                        }
                    }
                }
                if (quit) break;
                end = Math.min(numTotalHits, start + hitsPerPage);
            }
        }
    }
}