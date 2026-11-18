# Fooipot

Fooi eerlijk verdelen in de horeca: tel de pot (munten, biljetten en pinfooi), vul de uren in en je hebt meteen per collega het bedrag, met een voorstel voor wat er in elke envelop moet. Delen gaat via WhatsApp. Per maand zie je een overzicht dat je kunt kopiëren naar Excel voor de loonadministratie.

Het is één HTML-bestand (`index.html`) en werkt offline. Alles blijft op het toestel.

## Wat er nu in zit (versie 1)
- **Tel de pot**: per munt en biljet tellen, of alleen een totaalbedrag invullen, plus pinfooi uit de kassa.
- **Wie werkte er**: verdelen naar uren of gelijk, met een weging per persoon (bv. 0,5 voor afwas of een stagiair).
- **Verdeling**: kassabon met het bedrag per persoon, contant afgerond op 5 ct, 50 ct of € 1. Wat overblijft, blijft in de pot voor de volgende keer.
- **Envelopjes**: welke biljetten en munten in welke envelop gaan, met een melding als er kleingeld te weinig is.
- **Overzicht**: per maand uren, contant, via loon en totaal per persoon. Kopiëren voor Excel.
- **Team**: namen, weging en de naam van de zaak.

## Verdienmodel (plan)
- **Gratis**: tellen, verdelen en delen via WhatsApp. Zo gaat het rond onder personeel.
- **Pro, ca. € 4 per maand of € 39 per jaar per zaak**: maandoverzicht en export voor de loonadministratie, langere geschiedenis, meerdere zaken, eigen logo op de bon.
- Betalen via iDEAL (Mollie of Stripe), met een licentiecode die de Pro-functies ontgrendelt.

## Volgende stappen
1. Testen met 2 à 3 echte zaken in de buurt (café, lunchroom, snackbar) en vragen wat ze missen.
2. Online zetten als installeerbare website-app (GitHub Pages + manifest), met een eigen domeinnaam.
3. Pro-functies en betaling toevoegen.
4. Een simpele verkooppagina en de WhatsApp-bon als reclame ("Verdeeld met Fooipot").
