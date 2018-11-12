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
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
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
                "Usage:\tjava org.apache.lucene.demo.SearchFiles [-index dir] [-infoNeeds file] [-output file]\n\nSee http://lucene.apache.org/core/4_1_0/demo/ for details.";
        if (args.length > 0 && ("-h".equals(args[0]) || "-help".equals(args[0]) || args.length !=6)) {
            System.out.println(usage);
            System.exit(0);
        }

        String index = "index";
        String infoNeeds = null;
        String output = null;


        String queryString = null;


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
            }
        }

        File fichero = new File(output);
        BufferedWriter out = new BufferedWriter(new FileWriter(fichero, false));
        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(index)));
        IndexSearcher searcher = new IndexSearcher(reader);
        Analyzer analizadorBusqueda = new AnalizadorBusqueda();
        Analyzer analizadorAutores = new AnalizadorAutores();


        QueryParser titleParser = new QueryParser("title", analizadorBusqueda);
        QueryParser descriptionParser = new QueryParser("description", analizadorBusqueda);
        QueryParser subjectParser = new QueryParser("subject", analizadorBusqueda);
        QueryParser typeParser = new QueryParser("type", analizadorBusqueda);
        QueryParser creatorParser = new QueryParser("creator", analizadorAutores);


        /**
         * Cogemos las consultas del fichero XML infoNeeds
         */

        File inputFile = new File(infoNeeds);
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

        for (Map.Entry<String, String> entry : consultas.entrySet()) {

            String consulta = entry.getValue();
            String identifier = entry.getKey();
            System.out.println(consulta);

            if (consulta == null || consulta.length() == -1) {
                break;
            }

            consulta = consulta.trim();
            if (consulta.length() == 0) {
                break;
            }

            String anyo = "[0-9]{4}";
            String nombrePropio = "María";
            String finMaster = "Fin de Máster";
            String finGrado = "fin de grado";

            Pattern patronIntervalo = Pattern.compile(anyo + "-" + anyo + "|" + anyo + " y " + anyo);
            Pattern patronUltimos = Pattern.compile("últimos [0-9]+ años");
            Pattern patronMaster = Pattern.compile(finMaster);
            Pattern patronGrado = Pattern.compile(finGrado);
            Pattern patronNombre = Pattern.compile(nombrePropio);
            Matcher matcherIntervalo = patronIntervalo.matcher(consulta);
            Matcher matcherUltimos = patronUltimos.matcher(consulta);
            Matcher matcherMaster = patronMaster.matcher(consulta);
            Matcher matcherGrado = patronGrado.matcher(consulta);
            Matcher matcherNombre = patronNombre.matcher(consulta);


            BooleanQuery query = null;
            if (matcherIntervalo.find()) {

                Query queryTitulo = titleParser.parse(consulta);
                BoostQuery queryTituloFin = new BoostQuery(queryTitulo, 2);
                Query querySubject = subjectParser.parse(consulta);
                BoostQuery querySubjectFin = new BoostQuery(querySubject, 1.5f);
                Query queryDescription = descriptionParser.parse(consulta);
                BoostQuery queryDescriptionFin = new BoostQuery(queryDescription, 1);
                String fInicio = matcherIntervalo.group().substring(0, 4);
                String fFin = matcherIntervalo.group().substring(matcherIntervalo.group().length() - 4, matcherIntervalo.group().length());
                Query queryIntervalo = TermRangeQuery.newStringRange("date", fInicio, fFin, true, true);
                query = new BooleanQuery.Builder()
                        .add(queryTituloFin, BooleanClause.Occur.SHOULD)
                        .add(querySubjectFin, BooleanClause.Occur.SHOULD)
                        .add(queryDescriptionFin, BooleanClause.Occur.SHOULD)
                        .add(queryIntervalo, BooleanClause.Occur.MUST)
                        .build();
            } else if (matcherUltimos.find()) {
                Query queryTitulo = titleParser.parse(consulta);
                BoostQuery queryTituloFin = new BoostQuery(queryTitulo, 2);
                Query querySubject = subjectParser.parse(consulta);
                BoostQuery querySubjectFin = new BoostQuery(querySubject, 1.5f);
                Query queryDescription = descriptionParser.parse(consulta);
                BoostQuery queryDescriptionFin = new BoostQuery(queryDescription, 1);
                String auxString = matcherUltimos.group();
                auxString = auxString.replaceAll("últimos ", "");
                auxString = auxString.replaceAll(" años", "");
                Integer offset = Integer.parseInt(auxString);
                Integer intFecha = 2018 - offset;
                String fInicio = intFecha.toString();
                Query queryIntervalo = TermRangeQuery.newStringRange("date", fInicio, "2018", true, true);
                query = new BooleanQuery.Builder()
                        .add(queryTituloFin, BooleanClause.Occur.SHOULD)
                        .add(querySubjectFin, BooleanClause.Occur.SHOULD)
                        .add(queryDescriptionFin, BooleanClause.Occur.SHOULD)
                        .add(queryIntervalo, BooleanClause.Occur.MUST)
                        .build();
            } else if (matcherNombre.find()) {
                Query queryTitulo = titleParser.parse(consulta);
                BoostQuery queryTituloFin = new BoostQuery(queryTitulo, 2);
                Query querySubject = subjectParser.parse(consulta);
                BoostQuery querySubjectFin = new BoostQuery(querySubject, 1.5f);
                Query queryDescription = descriptionParser.parse(consulta);
                BoostQuery queryDescriptionFin = new BoostQuery(queryDescription, 1);
                Query queryCreator = creatorParser.parse(matcherNombre.group());
                query = new BooleanQuery.Builder()
                        .add(queryTituloFin, BooleanClause.Occur.SHOULD)
                        .add(querySubjectFin, BooleanClause.Occur.SHOULD)
                        .add(queryDescriptionFin, BooleanClause.Occur.SHOULD)
                        .add(queryCreator, BooleanClause.Occur.MUST)
                        .build();
            } else if (matcherGrado.find() && !matcherMaster.find()) {
                Query queryTitulo = titleParser.parse(consulta);
                BoostQuery queryTituloFin = new BoostQuery(queryTitulo, 2);
                Query querySubject = subjectParser.parse(consulta);
                BoostQuery querySubjectFin = new BoostQuery(querySubject, 1.5f);
                Query queryDescription = descriptionParser.parse(consulta);
                BoostQuery queryDescriptionFin = new BoostQuery(queryDescription, 1);
                Query queryTipo = typeParser.parse("info:eu-repo/semantics/bachelorThesis");

                query = new BooleanQuery.Builder()
                        .add(queryTituloFin, BooleanClause.Occur.SHOULD)
                        .add(querySubjectFin, BooleanClause.Occur.SHOULD)
                        .add(queryDescriptionFin, BooleanClause.Occur.SHOULD)
                        .add(queryTipo, BooleanClause.Occur.MUST)
                        .build();
            } else if (matcherMaster.find() && !matcherGrado.find()) {
                Query queryTitulo = titleParser.parse(consulta);
                BoostQuery queryTituloFin = new BoostQuery(queryTitulo, 2);
                Query querySubject = subjectParser.parse(consulta);
                BoostQuery querySubjectFin = new BoostQuery(querySubject, 1.5f);
                Query queryDescription = descriptionParser.parse(consulta);
                BoostQuery queryDescriptionFin = new BoostQuery(queryDescription, 1);
                Query queryTipo = typeParser.parse("info:eu-repo/semantics/masterThesis");
                query = new BooleanQuery.Builder()
                        .add(queryTituloFin, BooleanClause.Occur.SHOULD)
                        .add(querySubjectFin, BooleanClause.Occur.SHOULD)
                        .add(queryDescriptionFin, BooleanClause.Occur.SHOULD)
                        .add(queryTipo, BooleanClause.Occur.MUST)
                        .build();
            } else if (matcherMaster.find() && matcherGrado.find()) {
                Query queryTitulo = titleParser.parse(consulta);
                BoostQuery queryTituloFin = new BoostQuery(queryTitulo, 2);
                Query querySubject = subjectParser.parse(consulta);
                BoostQuery querySubjectFin = new BoostQuery(querySubject, 1.5f);
                Query queryDescription = descriptionParser.parse(consulta);
                BoostQuery queryDescriptionFin = new BoostQuery(queryDescription, 1);
                Query queryTipo = typeParser.parse("info:eu-repo/semantics/masterThesis");
                Query queryTipo2 = typeParser.parse("info:eu-repo/semantics/bachelorThesis");
                query = new BooleanQuery.Builder()
                        .add(queryTituloFin, BooleanClause.Occur.SHOULD)
                        .add(querySubjectFin, BooleanClause.Occur.SHOULD)
                        .add(queryDescriptionFin, BooleanClause.Occur.SHOULD)
                        .add(queryTipo, BooleanClause.Occur.MUST)
                        .add(queryTipo2, BooleanClause.Occur.MUST)
                        .build();
            } else {
                Query queryTitulo = titleParser.parse(consulta);
                BoostQuery queryTituloFin = new BoostQuery(queryTitulo, 2);
                Query querySubject = subjectParser.parse(consulta);
                BoostQuery querySubjectFin = new BoostQuery(querySubject, 1.5f);
                Query queryDescription = descriptionParser.parse(consulta);
                BoostQuery queryDescriptionFin = new BoostQuery(queryDescription, 1);
                query = new BooleanQuery.Builder()
                        .add(queryTituloFin, BooleanClause.Occur.SHOULD)
                        .add(querySubjectFin, BooleanClause.Occur.SHOULD)
                        .add(queryDescriptionFin, BooleanClause.Occur.SHOULD)
                        .build();
            }

            doPagingSearch(out, searcher, query, identifier);

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
    public static void doPagingSearch(BufferedWriter bw, IndexSearcher searcher, Query query, String identifier) throws IOException {

        // Collect enough docs to show 5 pages
        TopDocs results = searcher.search(query, 15000);
        ScoreDoc[] hits = results.scoreDocs;

        int numTotalHits = (int) results.totalHits;
        System.out.println(numTotalHits + " total matching documents");


        for (int i = 0; i < numTotalHits; i++) {
            Document doc = searcher.doc(hits[i].doc);
            String path = doc.get("path");
            path = path.replaceAll("recordsdc\\\\", "");
            if (path != null) {
                bw.write(identifier + "\t" + path + "\r\n");
            } else {
                System.out.println((i + 1) + ". " + "No path for this document");
            }
        }
        System.out.println("FIN CONSULTA " + identifier);

    }
}