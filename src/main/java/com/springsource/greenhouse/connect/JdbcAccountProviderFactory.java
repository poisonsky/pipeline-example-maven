package com.springsource.greenhouse.connect;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.encrypt.StringEncryptor;
import org.springframework.social.facebook.FacebookOperations;
import org.springframework.social.facebook.FacebookTemplate;
import org.springframework.social.linkedin.LinkedInOperations;
import org.springframework.social.linkedin.LinkedInTemplate;
import org.springframework.social.tripit.TripItOperations;
import org.springframework.social.tripit.TripItTemplate;
import org.springframework.social.twitter.TwitterOperations;
import org.springframework.social.twitter.TwitterTemplate;

import com.springsource.greenhouse.account.AccountMapper;

//TODO this is disabled because it's needed in root-context yet scanned by app-servlet-context. not very clean: revisit this.
//@Repository
public class JdbcAccountProviderFactory implements AccountProviderFactory {
	
	private final JdbcTemplate jdbcTemplate;

	private final AccountMapper accountMapper;

	private final StringEncryptor encryptor;
	
	@Autowired
	public JdbcAccountProviderFactory(JdbcTemplate jdbcTemplate, StringEncryptor encryptor, AccountMapper accountMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.accountMapper = accountMapper;
		this.encryptor = encryptor;
	}

	@SuppressWarnings("unchecked")
	public <A> AccountProvider<A> getAccountProvider(Class<A> apiType) {
		if (FacebookOperations.class.equals(apiType)) {
			return (AccountProvider<A>) new JdbcAccountProvider<FacebookOperations>(getParameters("facebook"), jdbcTemplate, encryptor, accountMapper) {
				public FacebookOperations createApi(OAuthToken accessToken) {
					if (accessToken == null) {
						throw new IllegalStateException("Cannot access Facebook without an access token");
					}
					return new FacebookTemplate(accessToken.getValue());
				}
				public String getProviderAccountId(FacebookOperations api) {
					return api.getProfileId();
				}

				public String getProviderProfileUrl(FacebookOperations api) {
					return "http://www.facebook.com/profile.php?id=" + api.getProfileId();
				}
			};
		} else if (TwitterOperations.class.equals(apiType)) {
			return (AccountProvider<A>) new JdbcAccountProvider<TwitterOperations>(getParameters("twitter"), jdbcTemplate, encryptor, accountMapper) {
				public TwitterOperations createApi(OAuthToken accessToken) {
					return accessToken != null ? new TwitterTemplate(getApiKey(), getSecret(), accessToken.getValue(), accessToken.getSecret()) : new TwitterTemplate();
				}
				public String getProviderAccountId(TwitterOperations api) {
					return api.getProfileId();
				}

				public String getProviderProfileUrl(TwitterOperations api) {
					return "http://www.twitter.com/" + api.getProfileId();
				}				
			};			
		} else if (LinkedInOperations.class.equals(apiType)) {
			return (AccountProvider<A>) new JdbcAccountProvider<LinkedInOperations>(getParameters("linkedin"), jdbcTemplate, encryptor, accountMapper) {
				public LinkedInOperations createApi(OAuthToken accessToken) {
					if (accessToken == null) {
						throw new IllegalStateException("Cannot access LinkedIn without an access token");
					}
					return new LinkedInTemplate(getApiKey(), getSecret(), accessToken.getValue(), accessToken.getSecret());
				}
				public String getProviderAccountId(LinkedInOperations api) {
					return api.getProfileId();
				}

				public String getProviderProfileUrl(LinkedInOperations api) {
					return api.getProfileUrl();
				}				
			};
		} else if (TripItOperations.class.equals(apiType)) {
			return (AccountProvider<A>) new JdbcAccountProvider<TripItOperations>(getParameters("tripit"), jdbcTemplate, encryptor, accountMapper) {
				public TripItOperations createApi(OAuthToken accessToken) {
					if (accessToken == null) {
						throw new IllegalStateException("Cannot access TripIt without an access token");
					}
					return new TripItTemplate(getApiKey(), getSecret(), accessToken.getValue(), accessToken.getSecret());
				}
				public String getProviderAccountId(TripItOperations api) {
					return api.getProfileId();
				}

				public String getProviderProfileUrl(TripItOperations api) {
					return api.getProfileUrl();
				}
			};
		} else {
			throw new IllegalArgumentException("Not a supported apiType " + apiType);
		}
	}

	public AccountProvider<?> getAccountProviderByName(String name) {
		if (name.equals("facebook")) {
			return getAccountProvider(FacebookOperations.class);
		} else if (name.equals("twitter")) {
			return getAccountProvider(TwitterOperations.class);
		} else if (name.equals("linkedin")) {
			return getAccountProvider(LinkedInOperations.class);
		} else if (name.equals("tripit")) {
			return getAccountProvider(TripItOperations.class);
		} else {
			throw new IllegalArgumentException("Not a known provider: " + name);
		}
	}
	
	public List<ConnectedProfile> findConnectedProfiles(Long accountId) {
		return jdbcTemplate.query(SELECT_ACCOUNT_CONNECTIONS, new RowMapper<ConnectedProfile>() {
			public ConnectedProfile mapRow(ResultSet rs, int row) throws SQLException {
				return new ConnectedProfile(rs.getString("displayName"), rs.getString("profileUrl"));
			}
		}, accountId);
	}
	
	// internal helpers
	
	private AccountProviderParameters getParameters(final String provider) {
		return jdbcTemplate.queryForObject(SELECT_ACCOUNT_PROVIDER_BY_NAME, new RowMapper<AccountProviderParameters>() {
			public AccountProviderParameters mapRow(ResultSet rs, int rowNum) throws SQLException {
				return new AccountProviderParameters(provider, rs.getString("displayName"),
					encryptor.decrypt(rs.getString("apiKey")), encryptor.decrypt(rs.getString("secret")), rs.getLong("appId"),
					rs.getString("requestTokenUrl"), rs.getString("authorizeUrl"), rs.getString("accessTokenUrl"));
			}
		}, provider);
	}

	private static final String SELECT_ACCOUNT_PROVIDER_BY_NAME = "select displayName, apiKey, secret, appId, requestTokenUrl, authorizeUrl, accessTokenUrl from AccountProvider where name = ?";

	private static final String SELECT_ACCOUNT_CONNECTIONS = "select p.name, p.displayName, c.accountId, c.profileUrl from AccountConnection c inner join AccountProvider p on c.provider = p.name where member = ? order by displayName";
	
}