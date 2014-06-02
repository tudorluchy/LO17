package main;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletContext;

import org.antlr.runtime.ANTLRReaderStream;
import org.antlr.runtime.CommonTokenStream;

import sql.lexique.LexiqueCorpus;
import sql.lexique.LexiqueSimple;
import sql.requete.InterrogPostgresql;
import antlr.output.tal_sql10Lexer;
import antlr.output.tal_sql10Parser;


public class MainTomcat {

	/**
	 * Test search
	 * @param value
	 * @param prodNames
	 * @return
	 */
	public static boolean search(String value, List<String> prodNames) {
	    for (String name : prodNames) {
	        if (name.equals(value)) {
	            return true;
	        }
	    }
	    return false;
	}
	
	/**
	 * Test if string is integer
	 * @param s
	 * @return
	 */
	public static boolean isInteger(String s) {
	    try { 
	        Integer.parseInt(s); 
	    } catch(NumberFormatException e) { 
	        return false; 
	    }
	    // only got here if we didn't return false
	    return true;
	}
	
	/**
	 * Converts pseudo SQL to SQL
	 * @param pseudoSQL
	 * @return
	 */
	public static String pseudoSQLtoSQL(String pseudoSQL) {
		String res = pseudoSQL;
		
		// nettoyage
		res = res.replaceAll("\\(","");
		res = res.replaceAll("\\)","");
		res = res.replaceAll(";","");
		res = res.replaceAll(" {2,}"," ");
		res = res.trim();
		
		// param_select = page ou article ou rubrique
		String paramSelect = "";
		Pattern p1 = Pattern.compile("(distinct|count)\\s([a-zA-Z]+)\\s");
		Matcher m1 = p1.matcher(res);
		if (m1.find()) {
			paramSelect = m1.group(2);
		}
//		System.out.println("param_select :");
//		System.out.println(paramSelect);
		
		// tables de from
		ArrayList<String> tablesFrom = new ArrayList<String>();
		
		Pattern p = Pattern.compile("from\\s([a-zA-Z]+)\\s");
		Matcher m = p.matcher(res);
		while (m.find()) {
//			System.out.println(m.group(1));
			tablesFrom.add(m.group(1));
			res = res.replaceAll(m.group(0),"");
		}
		
		String chaineFrom = "";
		for (int i = 0; i < tablesFrom.size() - 1; i++) {
			chaineFrom = chaineFrom + tablesFrom.get(i) + ", ";
		}
		chaineFrom = chaineFrom + tablesFrom.get(tablesFrom.size() - 1);
		
//		System.out.println(chaineFrom);
		res = res.replaceFirst(" where"," from " + chaineFrom + " where");
		
		
		// Supprimer les WHERE inutiles
		res = res.replaceAll("AND where", "AND");
		res = res.replaceAll("OR where", "OR");
		
		// permet enlever ambiguité pour la colonne a selectionner
		res = res.replaceFirst(paramSelect, tablesFrom.get(0) + "." + paramSelect);
		
		// permet de gérer les jointures
		String jointure = "";
		for (int i = 0; i < tablesFrom.size() - 1; i++) {
			jointure = " AND " + tablesFrom.get(i)+ "." + paramSelect + " = " + tablesFrom.get(i+1)+ "." + paramSelect;
		}
//		System.out.println(jointure);
		res = res + jointure;
		
		// Pour reformuler les COUNT
		Pattern p2 = Pattern.compile("count");
		Matcher m2 = p2.matcher(res);
		boolean count = false;
		if (m2.find()) {
			res = res.replaceFirst("count(*)\\s", "count(" + paramSelect + ") ");
			count = true;
		}
		
		// Pour gérer les ORDER BY
		Pattern p3 = Pattern.compile("(order by date (ASC|DESC))");
		Matcher m3 = p3.matcher(res);
		boolean orderBy = false;
		if (m3.find()){ // déplace order by ... et le met à la fin de la requete 
			System.out.println(m3.group(1));
			res = res.replaceFirst(m3.group(1), "");
			res = res + m3.group(1);
			orderBy = true;
		}
		
		// Si pas de count et pas de order by alors on en crée un order par défault
		if (!count && !orderBy) {
			res = res + " ORDER BY " + tablesFrom.get(0) + "." + paramSelect;
		}
		
		System.out.println("SQL : " + res);
		return res;
	}
	
	/**
	 * Get a SQL query from natural language
	 * @param requeteNaturelle
	 * @param context
	 * @return
	 */
	public static String getRequeteNormalisee(String requeteNaturelle, ServletContext context) {
		
		InputStream is1 = context.getResourceAsStream("res/lemm_td2.txt");
		InputStream is2 = context.getResourceAsStream("res/dic_pivot.txt");
		InputStream is3 = context.getResourceAsStream("res/stopliste.txt");
		
		// les lexiques
		LexiqueCorpus lexiqueLemmes = new LexiqueCorpus(is1);
		LexiqueSimple lexiquePivot = new LexiqueSimple(is2);
		LexiqueSimple lexiqueStop = new LexiqueSimple(is3);
		// base
		InterrogPostgresql base = new InterrogPostgresql();
		
//		System.out.println("grand -- grenade");
//		System.out.println("Coût Levenshtein : " + lexiqueLemmes.calculCoutLev("grand", "grenade"));
//		System.out.println();
//		System.out.println("grenade -- grands");
//		System.out.println("Coût Levenshtein : " + lexiqueLemmes.calculCoutLev("grenade", "grands"));
//		System.out.println();
//		System.out.println("grenade -- grand");
//		System.out.println("Coût Levenshtein : " + lexiqueLemmes.calculCoutLev("grenade", "grand"));
//		System.out.println();
//		System.out.println("grenade -- e");
//		System.out.println("Coût Levenshtein : " + lexiqueLemmes.calculCoutLev("grenade", "e"));
		
		// strings
		String questionEnCours = "";
		String motQuestionEnCours = "";
		String reqNormalisee = "";

		// liste des mots pivot
		ArrayList<String> listeMotsPivot = new ArrayList<String>();
		
		if (requeteNaturelle == null) {
			return "";
		}
	
		questionEnCours = requeteNaturelle;
	
		if (!questionEnCours.trim().isEmpty()) {
			
			System.out.println("=====================================");
			
			System.out.println("Question : " + questionEnCours);
			
			// requete normalisée
			reqNormalisee = "";
			
			// init liste mots pivot
			listeMotsPivot = new ArrayList<String>();
			
			// question en minuscules
			questionEnCours = questionEnCours.toLowerCase();
			
			/**
			 * Traitements sur la question
			 */
			
			// suppression des caractères spéciaux + ponctuation
//				questionEnCours = questionEnCours.replaceAll("[\\p{Punct}ÀÁÂÄÇÈÉÊËÌÍÎÏÑÒÓÔÕÖÙÚÛÜÝàáâãäçèéêëìíîïñòóôõöùúûüýÿ]", "");
			
			// suppression du .
			questionEnCours = questionEnCours.replaceAll("\\w\\.\\s", "");
			// suppression du ?
			questionEnCours = questionEnCours.replaceAll("\\?", "");
			// suppression du !
			questionEnCours = questionEnCours.replaceAll("\\!", "");
			// suppression du "
			questionEnCours = questionEnCours.replaceAll("\"", "");
			// suppression du ,
			questionEnCours = questionEnCours.replaceAll(",", "");
			// suppression du ;
			questionEnCours = questionEnCours.replaceAll(";", "");
			
			// tokeniser la question 
			StringTokenizer st = new StringTokenizer(questionEnCours, " ");
//			System.out.println("Nombre de mots saisis : " + st.countTokens());
//			System.out.println();
			
			// parcours mots question
			while (st.hasMoreTokens()) {
				motQuestionEnCours = "";
				motQuestionEnCours = st.nextToken();
				
				/**
				 * Traitements sur chaque token
				 */
				// suppression du d'
				if ((motQuestionEnCours.charAt(0) == 'd') && (motQuestionEnCours.charAt(1) == '\'')) {
					motQuestionEnCours = motQuestionEnCours.replaceAll("d'", "");
				}
				// suppression du l'
				if ((motQuestionEnCours.charAt(0) == 'l') && (motQuestionEnCours.charAt(1) == '\'')) {
					motQuestionEnCours = motQuestionEnCours.replaceAll("l'", "");
				}
				// suppression du j'
				if ((motQuestionEnCours.charAt(0) == 'j') && (motQuestionEnCours.charAt(1) == '\'')) {
					motQuestionEnCours = motQuestionEnCours.replaceAll("j'", "");
				}
				
//					System.out.println("===***=============== " + motQuestionEnCours);
				
				if (isInteger(motQuestionEnCours)) {
					reqNormalisee = reqNormalisee.concat(motQuestionEnCours + " ");
				} else {
					// on traite seulement les mots d'au moins 2 lettres
					if (motQuestionEnCours.length() > 1) {
						if (lexiqueStop.getValeur(motQuestionEnCours) != null) {
							reqNormalisee = reqNormalisee.concat(lexiqueStop.getValeur(motQuestionEnCours) + " ");
//							System.out.println(motQuestionEnCours + " > stop mot");
						} else {
							if (lexiquePivot.getValeur(motQuestionEnCours) != null) {
								String motPivot = lexiquePivot.getValeur(motQuestionEnCours);
								// on ajoute le nouveau mot pivot dans la liste 
								if (!search(motPivot, listeMotsPivot)) {
									reqNormalisee = reqNormalisee.concat(motPivot + " ");
									listeMotsPivot.add(motPivot);
//									System.out.println("ajout pivot > " + motPivot);
								} else { // mot pivot déjà existant, on ajoute le mot lui-même
//									System.out.println("pivot déjà existant > " + motPivot);
									reqNormalisee = reqNormalisee.concat(motQuestionEnCours + " ");
								}
							} else {
								/**
								 *  On récupère le lemme - méthode simple
								 */
								String lemme = lexiqueLemmes.getLemme(motQuestionEnCours);
								// lemme exist
								if (lemme != null) {
				//					System.out.println("Ce mot est dans le lexique, son lemme est : " + lemme);
									// on prend le lemme trouvé
									reqNormalisee = reqNormalisee.concat(lemme + " ");
								// prefix
								} else {
									/**
									 * On récupère liste lemmes - méthode préfix
									 */
									HashMap<String, Integer> resPrefix = lexiqueLemmes.getPrefixList(motQuestionEnCours);
									if (!resPrefix.isEmpty()) {
				//						System.out.println("[Méthode préfixe] La liste des meilleurs lemmes candidats :");
				//						Lexique.afficherLemmProxBest(resPrefix);
										// par défaut on prend le premier lemme trouvé
										String lemmeChoisi = resPrefix.keySet().iterator().next();
				//						System.out.println("Lemme choisi : " + lemmeChoisi);
										reqNormalisee = reqNormalisee.concat(lemmeChoisi + " ");
									// Levenshtein
									} else {
										/**
										 * On récupère liste lemmes - méthode levenshtein
										 */
										HashMap<String, Integer> resLev = lexiqueLemmes.getLevenshteinList(motQuestionEnCours);
										if (!resLev.isEmpty()) {
				//							System.out.println("[Méthode Levenshtein] La liste des meilleurs lemmes candidats :");
//											Lexique.afficherLemmLevenshteinBest(resLev);
											// par défaut on prend le premier lemme trouvé
											String lemmeChoisi = resLev.keySet().iterator().next();
//											System.out.println("Lemme choisi : " + lemmeChoisi);
											reqNormalisee = reqNormalisee.concat(lemmeChoisi + " ");
										// retourne mot
										} else {
											/**
											 * On retourne le mot
											 */
				//							System.out.println("Aucun mot n'a été trouvé.");
											reqNormalisee = reqNormalisee.concat(motQuestionEnCours + " ");
										}
									}
								}
							}
						}
					}
				}
			}
			
			/**
			 * Affichage requete normalisée 1
			 */
			System.out.println("Requête normalisée étape 1 : " + reqNormalisee);
			
			/**
			 * Quelques traitements sur la requete normalisée
			 * 
			 * on supprime les "stop"
			 * on supprime espace début, fin de chaine (trim)
			 * et les espaces en trop
			 * et on ajoute le point à la fin s'il n'y est pas
			 */
			reqNormalisee = reqNormalisee.replaceAll("stop", "");
			reqNormalisee = reqNormalisee.trim();
			reqNormalisee = reqNormalisee.replaceAll(" {2,}"," ");
			if (!(reqNormalisee.charAt(reqNormalisee.length() - 1) == '.')) {
				reqNormalisee = reqNormalisee.concat(".");
			}
			
			/**
			 * Affichage requete normalisée 2
			 */
			System.out.println("Requête normalisée étape 2 : " + reqNormalisee);
			
			
			/**
			 * Ajout des conjoctions sur requete normalisée finale
			 */
			
			// tokeniser la requete
			StringTokenizer st2 = new StringTokenizer(reqNormalisee, " ");
			StringTokenizer st3 = new StringTokenizer(reqNormalisee, " ");
			
			String reqNormalisee2 = "";

			boolean isEt = false;
			boolean isOu = false;
			
			ArrayList<String> etListe = new ArrayList<String>();
			etListe.add("titre");
			etListe.add("theme");
			etListe.add("mot");
			
			ArrayList<String> ouListe = new ArrayList<String>();
			ouListe.add("date");
			ouListe.add("email");
			
			ArrayList<String> etouListe = new ArrayList<String>();
			etouListe.addAll(etListe);
			etouListe.addAll(ouListe);
			etouListe.add("et");
			etouListe.add("ou");
			
			String motSuivant = st3.nextToken();
			
			// parcours mots requete
			while (st2.hasMoreTokens()) {
				String mot = st2.nextToken();
				
				try {
					motSuivant = st3.nextToken(); // indice + 1
					
					// on a déjà rencontré un type param
					if (isEt && (!search(mot, etouListe)) && !search(motSuivant, etouListe)) {
//							System.out.println("now => " + mot + " et ");
						reqNormalisee2 = reqNormalisee2.concat(mot + " et ");
					} else if (isOu && (!search(mot, etouListe)) && !search(motSuivant, etouListe)) {
//							System.out.println("now => " + mot + " ou ");
						reqNormalisee2 = reqNormalisee2.concat(mot + " ou ");
					} else {
//							System.out.println("now => " + mot);
						reqNormalisee2 = reqNormalisee2.concat(mot + " ");
					}
				} catch (Exception e) {
//						System.out.println("now => " + mot);
					reqNormalisee2 = reqNormalisee2.concat(mot + " ");
				}
				
				// switch entre "et" et "ou"
				if (search(mot, etListe)) {
					isEt = true;
					isOu = false;
				} else if (search(mot, ouListe)) {
					isOu = true;
					isEt = false;
					if (mot.equals("date")) {
						isOu = false;
					}
				}
			}
			
			/**
			 * Affichage requete normalisée 3
			 */
			System.out.println("Requête normalisée étape 3 : " + reqNormalisee2);
			System.out.println("=====================================");
			
			/**
			 * Requete normalisée => affichage requete sql
			 */
//			System.out.println("*****************");
			String arbre = null;
			try{
	    		tal_sql10Lexer lexer = new tal_sql10Lexer(new ANTLRReaderStream(new StringReader(reqNormalisee2)));
	      		CommonTokenStream tokens = new CommonTokenStream(lexer);
	      		tal_sql10Parser parser = new tal_sql10Parser(tokens);
				arbre = parser.listerequetes();
//				System.out.println(arbre);
	    	} catch(Exception e) {  }
//	    	System.out.println("*****************");
	    	
//	    	arbre = arbre.replaceAll("\\(", "");
//	    	arbre = arbre.replaceAll("\\)", "");
	    	arbre = arbre.replaceAll(" {2,}"," ");
	    	arbre = arbre.trim();
	    	arbre = arbre.concat(";");
	    	
	    	System.out.println("*****************");
	    	System.out.println(arbre);
	    	System.out.println("*****************");
	    	
//			base.setRequete(arbre);
//			base.exec_sql();
	    
	    	System.out.println();
	    	
	    	String sql = pseudoSQLtoSQL(arbre);
		
	    	return sql;
	    	
		}
		
		return "";
	}
}