package name.abuchen.portfolio.datatransfer.pdf;

import static name.abuchen.portfolio.util.TextUtil.trim;

import java.util.Locale;

import name.abuchen.portfolio.datatransfer.pdf.PDFParser.Block;
import name.abuchen.portfolio.datatransfer.pdf.PDFParser.DocumentType;
import name.abuchen.portfolio.datatransfer.pdf.PDFParser.Transaction;
import name.abuchen.portfolio.model.AccountTransaction;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.money.CurrencyUnit;

@SuppressWarnings("nls")
public class BondoraCapitalPDFExtractor extends AbstractPDFExtractor
{
    public BondoraCapitalPDFExtractor(Client client)
    {
        super(client);

        addBankIdentifier("Zusammenfassung"); //$NON-NLS-1$
        addBankIdentifier("Summary"); //$NON-NLS-1$

        addAccountStatementTransaction();
    }

    @Override
    public String getLabel()
    {
        return "Bondora Capital"; //$NON-NLS-1$
    }

    private void addAccountStatementTransaction()
    {
        final DocumentType type = new DocumentType("(Zusammenfassung|Summary)");
        this.addDocumentTyp(type);

        Block block = new Block("^([\\d]{2}.[\\d]{2}.[\\d]{4}|[\\d]{4}.[\\d]{2}.[\\d]{2}) .*$");
        type.addBlock(block);
        block.setMaxSize(1);

        Transaction<AccountTransaction> pdfTransaction = new Transaction<>();
        pdfTransaction.subject(() -> {
            AccountTransaction entry = new AccountTransaction();
            entry.setType(AccountTransaction.Type.INTEREST);
            return entry;
        });

        pdfTransaction
                .section("type").optional()
                .match("^([\\d]{2}.[\\d]{2}.[\\d]{4}|[\\d]{4}.[\\d]{2}.[\\d]{2}) "
                                + "(?<type>(.berweisen"
                                + "|Transfer"
                                + "|Abheben"
                                + "|Go & Grow Zinsen"
                                + "|Go & Grow returns"
                                + "|Withdrawal)"
                                + ") .*$")
                .assign((t, v) -> {
                    if (v.get("type").equals("Überweisen") || v.get("type").equals("Transfer"))
                        t.setType(AccountTransaction.Type.DEPOSIT);
                    else if (v.get("type").equals("Abheben") || v.get("type").equals("Withdrawal"))
                        t.setType(AccountTransaction.Type.REMOVAL);
                })

                .oneOf(
                        // @formatter:off
                        // 06.02.2022 Go & Grow Zinsen 0,22 € 1.228,18 €
                        // 07.02.2022 Überweisen 1.000 € 2.228,18 €
                        //
                        // 25.10.2020 Go & Grow Zinsen 1 € 5'630,99 €
                        // 26.10.2020 Go & Grow Zinsen 1,01 € 5'632 €
                        // @formatter:on
                        section -> section
                                .attributes("date", "note", "amount")
                                .match("^(?<date>([\\d]{2}\\.[\\d]{2}\\.[\\d]{4}|[\\d]{4}\\.[\\d]{2}\\.[\\d]{2})) "
                                                + "(?<note>(.berweisen"
                                                + "|Transfer"
                                                + "|Abheben"
                                                + "|Go & Grow Zinsen"
                                                + "|Go & Grow returns"
                                                + "|Withdrawal)) "
                                                + "(\\p{Sc})?(\\W)?"
                                                + "(?<amount>[\\.,'\\d\\s]+)"
                                                + "(\\W)?(\\p{Sc})(\\W)?(\\-)?[\\.,'\\d\\s]+(\\W)?(\\p{Sc})?$")
                                .assign((t, v) -> {
                                    t.setDateTime(asDate(v.get("date")));

                                    String language = "de"; //$NON-NLS-1$
                                    String country = "DE"; //$NON-NLS-1$

                                    int apostrophe = v.get("amount").indexOf("\'"); //$NON-NLS-1$
                                    if (apostrophe >= 0)
                                    {
                                        language = "de"; //$NON-NLS-1$
                                        country = "CH"; //$NON-NLS-1$
                                    }

                                    t.setAmount(asAmount(v.get("amount").trim().replaceAll("\\s", ""), language, country));
                                    t.setCurrencyCode(asCurrencyCode(CurrencyUnit.EUR));
                                    t.setNote(trim(v.get("note")));
                                })
                        ,
                        // @formatter:off
                        // 02/19/2023 Go & Grow Zinsen €1.62 €9,056.75
                        // 03/02/2023 Go & Grow Zinsen €1.62 €9,074.6
                        // 03/03/2023 Go & Grow Zinsen €1.62 €9,076.22
                        // 03/04/2023 Go & Grow Zinsen €1.63 €9,077.85
                        // @formatter:on
                        section -> section
                                .attributes("date", "note", "amount")
                                .match("^(?<date>([\\d]{2}\\/[\\d]{2}\\/[\\d]{4}|[\\d]{4}\\/[\\d]{2}\\/[\\d]{2})) "
                                                + "(?<note>(.berweisen"
                                                + "|Transfer"
                                                + "|Abheben"
                                                + "|Go & Grow Zinsen"
                                                + "|Go & Grow returns"
                                                + "|Withdrawal)) "
                                                + "(\\p{Sc})?(\\W)?"
                                                + "(?<amount>[\\.,\\d]+)"
                                                + "(\\W)?(\\p{Sc})(\\W)?(\\-)?[\\.,\\d]+(\\W)?(\\p{Sc})?$")
                                .assign((t, v) -> {
                                    t.setDateTime(asDate(v.get("date"), Locale.UK));
                                    t.setAmount(asAmount(v.get("amount"), "en", "US"));
                                    t.setCurrencyCode(asCurrencyCode(CurrencyUnit.EUR));
                                    t.setNote(trim(v.get("note")));
                                })
                        ,
                        // @formatter:off
                        // 06-10-2020 Überweisen 4,91 € 104,91 €
                        // 06-10-2020 Go & Grow Zinsen 0,02 € 154,93 €
                        // @formatter:on
                        section -> section
                                .attributes("date", "note", "amount")
                                .match("^(?<date>([\\d]{2}\\-[\\d]{2}\\-[\\d]{4}|[\\d]{4}\\-[\\d]{2}\\-[\\d]{2})) "
                                                + "(?<note>(.berweisen"
                                                + "|Transfer"
                                                + "|Abheben"
                                                + "|Go & Grow Zinsen"
                                                + "|Go & Grow returns"
                                                + "|Withdrawal)) "
                                                + "(\\p{Sc})?(\\W)?"
                                                + "(?<amount>[\\.,\\d]+)"
                                                + "(\\W)?(\\p{Sc})(\\W)?(\\-)?[\\.,\\d]+(\\W)?(\\p{Sc})?$")
                                .assign((t, v) -> {
                                    t.setDateTime(asDate(v.get("date")));
                                    t.setAmount(asAmount(v.get("amount"), "de", "DE"));
                                    t.setCurrencyCode(asCurrencyCode(CurrencyUnit.EUR));
                                    t.setNote(trim(v.get("note")));
                                })
                )

                .wrap(TransactionItem::new);

        block.set(pdfTransaction);
    }
}
