

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.spell.Dictionary;
import org.apache.lucene.search.spell.PlainTextDictionary;
import org.apache.lucene.search.spell.SpellChecker;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;


public class prueba {
    public static SpellChecker spellChecker;

    public static void loadDictionary() {
        try {
            File dir = new File("diccionario/");
            Directory directory = FSDirectory.open(Paths.get("diccionario/"));
            SpellChecker spellChecker = new SpellChecker(directory);
            Dictionary dictionary = new PlainTextDictionary(new File("diccionario.txt"));
            IndexWriterConfig config = new IndexWriterConfig();
            spellChecker.indexDictionary(dictionary, config, false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String performSpellCheck(String word) {
        try {
            String[] suggestions = spellChecker.suggestSimilar(word, 1);
            if (suggestions.length > 0) {
                return suggestions[0];
            }
            else {
                return word;
            }
        } catch (Exception e) {
            return "Error";
        }

    }
    public static void main(String[] args) throws Exception {
        prueba.loadDictionary();
        prueba.performSpellCheck("fotovolaica");

    }

}
